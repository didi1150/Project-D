package dev.core.item;

import java.util.List;

import dev.core.stat.StatModifier;

public interface Equipment {

	List<StatModifier> getModifiers();

	String getId();

	String getDisplayName();

	String getDescription();

	String modifiersToString();

}
