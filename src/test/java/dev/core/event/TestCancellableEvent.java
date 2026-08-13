package dev.core.event;

public class TestCancellableEvent extends Event implements CoreCancellable {

	private boolean cancelled = false;

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}
}
