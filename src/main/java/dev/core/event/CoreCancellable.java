package dev.core.event;

public interface CoreCancellable {

	public boolean isCancelled();

	public void setCancelled(boolean canceled);
}
