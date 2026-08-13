package dev.core.stat.modifier;

public enum StatModifierType {

	FLAT,			// +40 Just add a flat amount
	PERCENT_ADD,	// +10% Sums with other percentage add
	MULTIPLY,		// x1.3 Multiply with other multipliers
	OVERRIDE		// Ignore all previous ones, set to current (Ignore bonus)
	
}
