package dev.core.stat;

public enum StatTarget {
	CURRENT, // Targets current amount
	MAX,     // Targets max amount
	BOTH;    // Basically like override (If you change form that changes max hp but also heals someone to full hp)
}
