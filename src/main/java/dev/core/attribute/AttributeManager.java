package dev.core.attribute;

import java.util.EnumMap;
import java.util.LinkedList;
import java.util.Map;

public class AttributeManager {

	private final Map<AttributeType, Attribute> attributes = new EnumMap<AttributeType, Attribute>(AttributeType.class);
	private final Map<ResourceType, ResourceAttribute> resources = new EnumMap<ResourceType, ResourceAttribute>(
			ResourceType.class);

	public AttributeManager() {
		for (AttributeType attributeType : AttributeType.values()) {
			attributes.put(attributeType, new Attribute(attributeType, 100, new LinkedList<AttributeModifier>()));
		}
		resources.put(ResourceType.HEALTH,
				new ResourceAttribute(ResourceType.HEALTH, () -> 100 + getAttributeValue(AttributeType.MAX_HEALTH),
						() -> getAttributeValue(AttributeType.HEALTH_REGEN)));

		resources.put(ResourceType.MANA, new ResourceAttribute(ResourceType.MANA,
				() -> 50 + getAttributeValue(AttributeType.MANA), () -> getAttributeValue(AttributeType.MANA_REGEN)));
		resources.put(ResourceType.ENERGY, new ResourceAttribute(ResourceType.ENERGY, () -> 0f, () -> 0f));
	}

	public void update(float deltaTime) {
		for (ResourceAttribute res : resources.values()) {
			res.update(deltaTime);
		}

		// Update buffs/equipment effects
		// For the future
//		buffManager.update(deltaTime);
	}

	public float getAttributeValue(AttributeType type) {
		return attributes.get(type).getValue();
	}

}
