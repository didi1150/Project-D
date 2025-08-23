package dev.core.stat;

@FunctionalInterface
public interface ParamSupplier<T, P> {
	T get(P parameter);
}
