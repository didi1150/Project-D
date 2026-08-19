package dev.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.bukkit.event.BukkitEventBus;

/**
 * Verifies discovery works when the plugin is packaged as a jar that has no
 * {@code dev/} directory entries on the classpath resource lookup (the
 * "getResources finds nothing" production scenario). In that case the scanner
 * must fall back to enumerating the classloader's jar URLs directly.
 */
public class EventSubscriberScannerJarTest {

	private static class JarTestEvent extends Event {
	}

	@EventSubscriber
	public static class JarFixtureSubscriber {

		static final List<String> CALLS = new ArrayList<>();

		@Subscribe
		public void onJarEvent(JarTestEvent event) {
			CALLS.add("jar");
		}
	}

	/**
	 * URLClassLoader whose {@code getResources} does not delegate to its
	 * parent, simulating a plugin classloader that only sees its own jar.
	 */
	private static class NonDelegatingUrlClassLoader extends URLClassLoader {

		NonDelegatingUrlClassLoader(URL url, ClassLoader parent) {
			super(new URL[] { url }, parent);
		}

		@Override
		public Enumeration<URL> getResources(String name) throws IOException {
			return findResources(name);
		}
	}

	@TempDir
	Path tempDir;

	private EventBusInterface eventBus;

	@BeforeEach
	void setup() {
		eventBus = BukkitEventBus.getInstance();
		JarFixtureSubscriber.CALLS.clear();
		eventBus.getSubscribed().removeIf(action -> action.getType() == JarTestEvent.class);
	}

	@AfterEach
	void cleanup() {
		eventBus.getSubscribed().removeIf(action -> action.getType() == JarTestEvent.class);
	}

	@Test
	void testScanFallsBackToJarUrlEnumerationWhenClasspathResourceMissing() throws Exception {
		Path jarFile = buildJarWithoutDirectoryEntries();

		try (URLClassLoader loader = new NonDelegatingUrlClassLoader(jarFile.toUri().toURL(),
				getClass().getClassLoader())) {
			EventSubscriberScanner.scan(eventBus, "dev.core.event", loader);
		}

		eventBus.sendEvent(new JarTestEvent());
		assertEquals(List.of("jar"), JarFixtureSubscriber.CALLS);
	}

	private Path buildJarWithoutDirectoryEntries() throws Exception {
		String classResource = JarFixtureSubscriber.class.getName().replace('.', '/') + ".class";
		URL classUrl = JarFixtureSubscriber.class.getClassLoader().getResource(classResource);
		assertTrue(classUrl != null, "compiled fixture class should exist on the test classpath");
		Path classFile = Paths.get(classUrl.toURI());

		File jarFile = tempDir.resolve("fixture-no-directory-entries.jar").toFile();
		try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(jarFile))) {
			JarEntry entry = new JarEntry(classResource);
			jar.putNextEntry(entry);
			jar.write(Files.readAllBytes(classFile));
			jar.closeEntry();
		}
		return jarFile.toPath();
	}
}