package dev.core.attribute;

import java.util.Queue;

public class Attribute {

	private AttributeType attributeType;
	private float baseValue;
	private Queue<AttributeModifier> modifiers;

	public Attribute(AttributeType attributeType, float baseValue, Queue<AttributeModifier> modifiers) {
		this.attributeType = attributeType;
		this.baseValue = baseValue;
		this.modifiers = modifiers;
	}

	public float getValue() {

		float flat = 0;
		float percent = 0;

		for (AttributeModifier mod : modifiers) {
			if (mod.getModifierType() == ModifierType.FLAT) {
				flat += mod.getValue();
			} else if (mod.getModifierType() == ModifierType.PERCENT) {
				percent += mod.getValue();
			}
		}

		return (baseValue + flat) * (1 + percent / 100);
	}

	public void addAttributeModifier(AttributeModifier modifier) {
		this.modifiers.add(modifier);
	}

	public void removeModifier(String source) {
		this.modifiers.removeIf(m -> m.getSourceId().equals(source));
	}

	public void setBaseValue(float baseValue) {
		this.baseValue = baseValue;
	}

	public AttributeType getAttributeType() {
		return attributeType;
	}

}
