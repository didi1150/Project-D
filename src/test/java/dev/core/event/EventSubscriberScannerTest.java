package dev.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.bukkit.event.BukkitEventBus;

public class EventSubscriberScannerTest {

	private static class ScannerTestEvent extends Event {
	}

	private static class ScannerRedirectEvent extends Event {
	}

	@EventSubscriber
	public static class ScannerTestSubscriber {

		static final List<String> CALLS = new ArrayList<>();

		@Subscribe
		public void onScannerEvent(ScannerTestEvent event) {
			CALLS.add("normal");
		}

		@Subscribe(priority = EventAction.HIGHEST_PRIORITY)
		public void onScannerEventHigh(ScannerTestEvent event) {
			CALLS.add("highest");
		}

		@Subscribe
		public void onScannerRedirect(ScannerRedirectEvent event) {
			CALLS.add("redirect");
		}
	}

	public static class InvalidSubscriber {

		@Subscribe
		public void noParameters() {
		}

		@Subscribe
		public void tooManyParameters(ScannerTestEvent first, ScannerTestEvent second) {
		}
	}

	static class InjectionDependency {
	}

	@EventSubscriber
	public static class InjectionNoArgSubscriber {

		static final List<String> CALLS = new ArrayList<>();

		@Subscribe
		public void onScannerEvent(ScannerTestEvent event) {
			CALLS.add("no-arg");
		}
	}

	@EventSubscriber
	public static class InjectionParamSubscriber {

		static final List<String> CALLS = new ArrayList<>();

		private final InjectionDependency dependency;

		InjectionParamSubscriber(InjectionDependency dependency) {
			this.dependency = dependency;
		}

		@Subscribe
		public void onScannerEvent(ScannerTestEvent event) {
			CALLS.add("param:" + (dependency != null));
		}
	}

	private EventBusInterface eventBus;

	@BeforeEach
	void setup() {
		eventBus = BukkitEventBus.getInstance();
		ScannerTestSubscriber.CALLS.clear();
	}

	@AfterEach
	void cleanup() {
		eventBus.getSubscribed()
				.removeIf(action -> action.getType() == ScannerTestEvent.class
						|| action.getType() == ScannerRedirectEvent.class);
	}

	@Test
	void testScanDiscoversAndRegistersAnnotatedSubscribers() {
		EventSubscriberScanner.scan(eventBus, "dev.core.event");

		eventBus.sendEvent(new ScannerTestEvent());
		assertEquals(List.of("highest", "normal"), ScannerTestSubscriber.CALLS);

		eventBus.sendEvent(new ScannerRedirectEvent());
		assertEquals(List.of("highest", "normal", "redirect"), ScannerTestSubscriber.CALLS);
	}

	@Test
	void testRegisterExistingInstance() {
		EventSubscriberScanner.register(eventBus, new ScannerTestSubscriber());

		eventBus.sendEvent(new ScannerTestEvent());

		assertEquals(List.of("highest", "normal"), ScannerTestSubscriber.CALLS);
	}

	@Test
	void testRegisterWithInjectionCandidateSupportsNoArgConstructors() {
		InjectionNoArgSubscriber.CALLS.clear();
		Object instance = EventSubscriberScanner.register(eventBus, InjectionNoArgSubscriber.class,
				new InjectionDependency());

		assertNotNull(instance);
		eventBus.sendEvent(new ScannerTestEvent());
		assertEquals(List.of("no-arg"), InjectionNoArgSubscriber.CALLS);
	}

	@Test
	void testRegisterWithInjectionCandidateSatisfiesConstructorParameters() {
		InjectionParamSubscriber.CALLS.clear();
		Object instance = EventSubscriberScanner.register(eventBus, InjectionParamSubscriber.class,
				new InjectionDependency());

		assertNotNull(instance);
		eventBus.sendEvent(new ScannerTestEvent());
		assertEquals(List.of("param:true"), InjectionParamSubscriber.CALLS);
	}

	@Test
	void testSubscribeMethodsRequireExactlyOneParameter() {
		InvalidSubscriber invalid = new InvalidSubscriber();
		assertThrows(IllegalArgumentException.class, () -> EventSubscriberScanner.register(eventBus, invalid));
	}

}