package dev.core.attribute;

public class AttributeModifier {

	private String sourceId;
	private AttributeType attributeType;
	private ModifierType modifierType;
	private double value;

	public AttributeModifier(String sourceId, AttributeType attributeType, ModifierType modifierType, double value) {
		this.sourceId = sourceId;
		this.attributeType = attributeType;
		this.modifierType = modifierType;
		this.value = value;
	}

	public AttributeType getAttributeType() {
		return attributeType;
	}

	public ModifierType getModifierType() {
		return modifierType;
	}

	public String getSourceId() {
		return sourceId;
	}

	public double getValue() {
		return value;
	}
}
