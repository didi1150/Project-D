package dev.core.event;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Discovers classes annotated with {@link EventSubscriber} on the classpath,
 * instantiates them and registers every {@link Subscribe} annotated method as
 * an {@link EventAction} on the {@link EventBusInterface}.
 *
 * <p>
 * Subscriber instances are created via their no-arg constructor, or via a
 * constructor whose parameters can all be satisfied by the supplied
 * injection candidates.
 */
public final class EventSubscriberScanner {

    private EventSubscriberScanner() {
    }

    /**
     * Scans the classpath below the package of the caller (e.g. the plugin
     * main class) for {@link EventSubscriber} classes and registers them.
     *
     * @param bus                 the event bus to subscribe to
     * @param injectionCandidates objects available to satisfy subscriber
     *                            constructor parameters (e.g. the plugin
     *                            instance)
     */
    public static void scan(@NotNull EventBusInterface bus, Object... injectionCandidates) {
        String callerPackage = StackWalker.getInstance().walk(frames -> frames.skip(1).findFirst()
                .map(frame -> frame.getDeclaringClass().getPackageName()).orElse(""));
        scan(bus, callerPackage, injectionCandidates);
    }

    /**
     * Scans the given package (and all sub-packages) for {@link EventSubscriber}
     * classes and registers them.
     *
     * @param bus                 the event bus to subscribe to
     * @param rootPackage         package to scan recursively
     * @param injectionCandidates objects available to satisfy subscriber
     *                            constructor parameters (e.g. the plugin
     *                            instance)
     */
    public static void scan(@NotNull EventBusInterface bus, @NotNull String rootPackage, Object... injectionCandidates) {
        scan(bus, rootPackage, EventSubscriberScanner.class.getClassLoader(), injectionCandidates);
    }

    /**
     * Scans the given package (and all sub-packages) for {@link EventSubscriber}
     * classes using the given classloader, and registers them.
     *
     * @param bus                 the event bus to subscribe to
     * @param rootPackage         package to scan recursively
     * @param primaryClassLoader  the classloader that loads the classes to scan
     *                            (usually the plugin's classloader)
     * @param injectionCandidates objects available to satisfy subscriber
     *                            constructor parameters (e.g. the plugin
     *                            instance)
     */
    public static void scan(@NotNull EventBusInterface bus, @NotNull String rootPackage,
            @NotNull ClassLoader primaryClassLoader, Object... injectionCandidates) {
        List<Class<?>> subscriberTypes = discover(rootPackage, primaryClassLoader);
        log("Scanning package '" + rootPackage + "': discovered " + subscriberTypes.size()
                + " @EventSubscriber class(es) with " + injectionCandidates.length + " injection candidate(s).");
        int registered = 0;
        int skipped = 0;
        for (Class<?> subscriberType : subscriberTypes) {
            Object instance = register(bus, subscriberType, injectionCandidates);
            if (instance != null) {
                registered++;
            } else {
                skipped++;
            }
        }
        log("Scan complete: " + registered + " subscriber(s) registered, " + skipped + " skipped.");
    }

    /**
     * Instantiates the given {@link EventSubscriber} type and registers all of
     * its {@link Subscribe} annotated methods.
     *
     * @return the created instance, or {@code null} if the type could not be
     *         instantiated
     */
    @Nullable
    public static Object register(@NotNull EventBusInterface bus, @NotNull Class<?> subscriberType,
            Object... injectionCandidates) {
        Object instance = instantiate(subscriberType, injectionCandidates);
        if (instance != null) {
            register(bus, instance);
        }
        return instance;
    }

    /**
     * Registers every {@link Subscribe} annotated method of an existing
     * instance on the bus.
     */
    public static void register(@NotNull EventBusInterface bus, @NotNull Object subscriber) {
        int count = 0;
        for (Method method : subscriber.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Subscribe.class)) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1) {
                throw new IllegalArgumentException(
                        "@Subscribe method " + method + " must have exactly one event parameter");
            }
            Subscribe subscribe = method.getAnnotation(Subscribe.class);
            registerMethod(bus, subscriber, method, parameters[0], subscribe.priority());
            log("  registered " + subscriber.getClass().getSimpleName() + "." + method.getName()
                    + " (" + parameters[0].getSimpleName() + ", priority " + subscribe.priority() + ")");
            count++;
        }
        if (count == 0) {
            log("  WARNING: " + subscriber.getClass().getName() + " has no @Subscribe methods to register.");
        }
    }

    private static void registerMethod(EventBusInterface bus, Object subscriber, Method method, Class<?> eventType,
            int priority) {
        method.setAccessible(true);
        bus.subscribe(new EventAction<>(event -> invoke(subscriber, method, event), eventType, priority));
    }

    private static void invoke(Object subscriber, Method method, Object event) {
        try {
            method.invoke(subscriber, event);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException("Event handler threw an exception", cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not access event handler " + method, e);
        }
    }

    @Nullable
    private static Object instantiate(Class<?> subscriberType, Object[] injectionCandidates) {
        if (subscriberType.isInterface() || subscriberType.isEnum()
                || Modifier.isAbstract(subscriberType.getModifiers())) {
            return null;
        }
        Constructor<?> constructor = resolveConstructor(subscriberType, injectionCandidates);
        if (constructor == null) {
            log("skipping " + subscriberType.getName()
                    + " - no suitable constructor (constructor parameters "
                    + "could not be satisfied by the injection candidates).");
            return null;
        }
        log("Instantiating " + subscriberType.getName() + " via constructor "
                + Arrays.toString(constructor.getParameterTypes()));
        try {
            constructor.setAccessible(true);
            Object instance = constructor.newInstance(matchArguments(constructor, injectionCandidates));
            log("Instantiated " + subscriberType.getName() + " successfully.");
            return instance;
        } catch (InstantiationException | IllegalAccessException e) {
            log("skipping " + subscriberType.getName() + " - could not instantiate: " + e.getMessage());
            return null;
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Could not instantiate subscriber " + subscriberType.getName(),
                    e.getCause());
        }
    }

    /**
     * Builds the argument list for the resolved constructor: positional when
     * the arity matches, otherwise one candidate per parameter, matched by
     * assignability.
     */
    private static Object[] matchArguments(Constructor<?> constructor, Object[] injectionCandidates) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        if (parameterTypes.length == injectionCandidates.length) {
            return injectionCandidates;
        }
        Object[] arguments = new Object[parameterTypes.length];
        boolean[] used = new boolean[injectionCandidates.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            for (int j = 0; j < injectionCandidates.length; j++) {
                if (!used[j] && parameterTypes[i].isInstance(injectionCandidates[j])) {
                    arguments[i] = injectionCandidates[j];
                    used[j] = true;
                    break;
                }
            }
            if (arguments[i] == null) {
                throw new IllegalStateException("No injection candidate for parameter " + i + " of "
                        + constructor);
            }
        }
        return arguments;
    }

    @Nullable
    private static Constructor<?> resolveConstructor(Class<?> subscriberType, Object[] injectionCandidates) {
        Constructor<?>[] constructors = subscriberType.getDeclaredConstructors();
        Arrays.sort(constructors, (a, b) -> b.getParameterCount() - a.getParameterCount());
        for (Constructor<?> constructor : constructors) {
            if (argsSatisfiable(constructor, injectionCandidates)) {
                return constructor;
            }
        }
        return null;
    }

    private static boolean argsSatisfiable(Constructor<?> constructor, Object[] injectionCandidates) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        if (parameterTypes.length > injectionCandidates.length) {
            return false;
        }
        for (Class<?> parameterType : parameterTypes) {
            if (!hasCandidate(parameterType, injectionCandidates)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasCandidate(Class<?> parameterType, Object[] injectionCandidates) {
        for (Object candidate : injectionCandidates) {
            if (candidate != null && parameterType.isInstance(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static List<Class<?>> discover(String rootPackage, ClassLoader primaryClassLoader) {
        rootPackage = rootPackage.strip().isEmpty() ? "dev" : rootPackage;
        String rootPath = rootPackage.replace('.', '/');
        List<Class<?>> found = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        List<ClassLoader> classLoaders = new ArrayList<>();
        addClassLoader(classLoaders, primaryClassLoader);
        addClassLoader(classLoaders, Thread.currentThread().getContextClassLoader());
        for (ClassLoader classLoader : classLoaders) {
            log("Discovery classloader: " + classLoader);
            try {
                discoverFromResources(classLoader, rootPackage, rootPath, found, seenUrls);
                if (found.isEmpty()) {
                    log("No @EventSubscriber classes found via classpath resources for '" + rootPath
                            + "' on " + classLoader + "; enumerating its URLs.");
                    discoverFromClassloaderUrls(classLoader, rootPackage, rootPath, found, seenUrls);
                }
            } catch (IOException | URISyntaxException e) {
                log("could not scan classloader " + classLoader + ": " + e.getMessage());
            }
            if (!found.isEmpty()) {
                log("Discovered " + found.size() + " @EventSubscriber class(es) via " + classLoader + ".");
                break;
            }
        }
        if (found.isEmpty()) {
            log("WARNING: no @EventSubscriber classes discovered for package '" + rootPackage + "'.");
        }
        Set<String> seenNames = new HashSet<>();
        found.removeIf(type -> !seenNames.add(type.getName()));
        return found;
    }

    private static void addClassLoader(List<ClassLoader> classLoaders, ClassLoader classLoader) {
        if (classLoader != null && !classLoaders.contains(classLoader)) {
            classLoaders.add(classLoader);
        }
    }

    private static void discoverFromResources(ClassLoader classLoader, String rootPackage, String rootPath,
            List<Class<?>> found, Set<String> seenUrls) throws IOException, URISyntaxException {
        Enumeration<URL> resources = classLoader.getResources(rootPath);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            if (!seenUrls.add(url.toExternalForm())) {
                log("Classpath resource '" + rootPath + "' -> " + url + " already scanned; skipped.");
                continue;
            }
            log("Classpath resource '" + rootPath + "' -> " + url + " (protocol: " + url.getProtocol() + ")");
            if ("file".equals(url.getProtocol())) {
                discoverFromDirectory(Paths.get(url.toURI()), rootPackage, classLoader, found);
            } else if ("jar".equals(url.getProtocol())) {
                discoverFromJar(((JarURLConnection) url.openConnection()).getJarFile(), rootPath, classLoader, found);
            }
        }
    }

    private static void discoverFromClassloaderUrls(ClassLoader classLoader, String rootPackage, String rootPath,
            List<Class<?>> found, Set<String> seenUrls) {
        if (!(classLoader instanceof URLClassLoader urlClassLoader)) {
            log("Classloader " + classLoader + " is not a URLClassLoader, cannot enumerate its URLs.");
            return;
        }
        for (URL url : urlClassLoader.getURLs()) {
            if (!seenUrls.add(url.toExternalForm())) {
                log("Classloader URL candidate: " + url + " already scanned; skipped.");
                continue;
            }
            log("Classloader URL candidate: " + url);
            try {
                if ("jar".equals(url.getProtocol())) {
                    discoverFromJar(((JarURLConnection) url.openConnection()).getJarFile(), rootPath, classLoader,
                            found);
                } else if ("file".equals(url.getProtocol())) {
                    Path path = Paths.get(url.toURI());
                    if (Files.isDirectory(path)) {
                        Path candidate = path.resolve(rootPath);
                        if (Files.isDirectory(candidate)) {
                            discoverFromDirectory(candidate, rootPackage, classLoader, found);
                        } else {
                            log("  " + path + " is a directory without a '" + rootPath + "' subpath; skipped.");
                        }
                    } else if (path.getFileName().toString().endsWith(".jar")) {
                        try (JarFile jar = new JarFile(path.toFile())) {
                            discoverFromJar(jar, rootPath, classLoader, found);
                        }
                    } else {
                        log("  " + path + " is neither a jar nor a directory; skipped.");
                    }
                } else {
                    log("  Unsupported protocol '" + url.getProtocol() + "' on " + url + "; skipped.");
                }
            } catch (IOException | URISyntaxException e) {
                log("  could not inspect " + url + ": " + e.getMessage());
            }
        }
    }

    private static void discoverFromDirectory(Path directory, String packageName, ClassLoader classLoader,
            List<Class<?>> found) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            for (Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    discoverFromDirectory(entry, packageName + "." + name, classLoader, found);
                } else if (name.endsWith(".class")) {
                    loadAndCheck(packageName + "." + name.substring(0, name.length() - ".class".length()),
                            classLoader, found);
                }
            }
        }
    }

    private static void discoverFromJar(JarFile jar, String rootPath, ClassLoader classLoader, List<Class<?>> found) {
        String prefix = rootPath + "/";
        int classCount = 0;
        int annotatedCount = 0;
        for (JarEntry entry : jar.stream().toList()) {
            if (!entry.getName().startsWith(prefix) || !entry.getName().endsWith(".class")) {
                continue;
            }
            classCount++;
            String className = entry.getName().substring(0, entry.getName().length() - ".class".length())
                    .replace('/', '.');
            if (loadAndCheck(className, classLoader, found)) {
                annotatedCount++;
            }
        }
        log("Scanned " + jar.getName() + ": " + classCount + " class(es) under '" + prefix + "', "
                + annotatedCount + " annotated, " + found.size() + " total found so far.");
    }

    private static boolean loadAndCheck(String className, ClassLoader classLoader, List<Class<?>> found) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            if (type.isAnnotationPresent(EventSubscriber.class)) {
                found.add(type);
                log("Found @EventSubscriber class " + className);
                return true;
            }
        } catch (ClassNotFoundException | LinkageError e) {
            log("could not inspect class " + className + ": " + e.getMessage());
        }
        return false;
    }

    private static void log(String message) {
        System.out.println("[EventSubscriberScanner] " + message);
    }
}