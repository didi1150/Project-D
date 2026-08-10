package dev.core.entity.boss;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GameEvent {
    private final String type;
    private final Map<String, Object> fields;

    public GameEvent(String type, Map<String, Object> fields) {
        this.type = type;
        this.fields = fields == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(fields));
    }

    public String type() {
        return type;
    }

    public Object get(String key) {
        return fields.get(key);
    }

    public Map<String, Object> fields() {
        return fields;
    }
}
