package dev.core.event;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
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
        List<Class<?>> subscriberTypes = discover(rootPackage);
        for (Class<?> subscriberType : subscriberTypes) {
            register(bus, subscriberType, injectionCandidates);
        }
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
            System.out.println("EventSubscriberScanner: skipping " + subscriberType.getName()
                    + " - no suitable constructor (no no-arg constructor and constructor parameters "
                    + "could not be satisfied by the injection candidates).");
            return null;
        }
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(injectionCandidates);
        } catch (InstantiationException | IllegalAccessException e) {
            System.out.println("EventSubscriberScanner: skipping " + subscriberType.getName()
                    + " - could not instantiate: " + e.getMessage());
            return null;
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Could not instantiate subscriber " + subscriberType.getName(),
                    e.getCause());
        }
    }

    @Nullable
    private static Constructor<?> resolveConstructor(Class<?> subscriberType, Object[] injectionCandidates) {
        try {
            if (injectionCandidates.length == 0) {
                return subscriberType.getDeclaredConstructor();
            }
        } catch (NoSuchMethodException ignored) {
            // fall through to constructor matching
        }
        for (Constructor<?> constructor : subscriberType.getDeclaredConstructors()) {
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

    private static List<Class<?>> discover(String rootPackage) {
        rootPackage = rootPackage.strip().isEmpty() ? "dev" : rootPackage;
        String rootPath = rootPackage.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<Class<?>> found = new ArrayList<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(rootPath);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if ("file".equals(url.getProtocol())) {
                    discoverFromDirectory(Paths.get(url.toURI()), rootPackage, classLoader, found);
                } else if ("jar".equals(url.getProtocol())) {
                    discoverFromJar((JarURLConnection) url.openConnection(), rootPath, classLoader, found);
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("EventSubscriberScanner: could not scan package " + rootPackage, e);
        }
        return found;
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

    private static void discoverFromJar(JarURLConnection connection, String rootPath, ClassLoader classLoader,
            List<Class<?>> found) throws IOException {
        String prefix = rootPath + "/";
        try (var jar = connection.getJarFile()) {
            jar.stream().filter(entry -> entry.getName().startsWith(prefix) && entry.getName().endsWith(".class"))
                    .map(entry -> entry.getName().substring(0, entry.getName().length() - ".class".length())
                            .replace('/', '.'))
                    .forEach(className -> loadAndCheck(className, classLoader, found));
        }
    }

    private static void loadAndCheck(String className, ClassLoader classLoader, List<Class<?>> found) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            if (type.isAnnotationPresent(EventSubscriber.class)) {
                found.add(type);
            }
        } catch (ClassNotFoundException | LinkageError e) {
            System.out.println("EventSubscriberScanner: could not inspect class " + className + ": " + e.getMessage());
        }
    }
}