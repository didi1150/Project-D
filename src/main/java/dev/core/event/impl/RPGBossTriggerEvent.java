package dev.core.event.impl;

import java.util.Collections;
import java.util.Map;

public class RPGBossTriggerEvent {

    private final String type;
    private final Map<String, Object> fields;

    public RPGBossTriggerEvent(String type, Map<String, Object> fields) {
        this.type = type;
        this.fields = Collections.unmodifiableMap(fields);
    }

    public String type() {
        return type;
    }

    public Object get(String field) {
        return fields.get(field);
    }

    public Map<String, Object> fields() {
        return fields;
    }

    @Override
    public String toString() {
        return "RPGBossTriggerEvent{" + type + ", " + fields + "}";
    }
}
