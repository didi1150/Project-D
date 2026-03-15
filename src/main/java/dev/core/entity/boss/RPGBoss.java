package dev.core.entity.boss;

import java.util.UUID;

import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.stat.StatManager;

public abstract class RPGBoss extends RPGEntity{

    public RPGBoss(StatManager statManager, UUID uuid, String name, EntityType entityType,
            EffectManagerInterface effectManagerInterface, EventBusInterface eventBusInterface,
            RPGClassType classType) {
        super(statManager, uuid, name, entityType, effectManagerInterface, eventBusInterface, classType);
    }

}
