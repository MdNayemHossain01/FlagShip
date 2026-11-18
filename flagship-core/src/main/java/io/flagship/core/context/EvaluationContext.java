package io.flagship.core.context;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class EvaluationContext {
    private final String userKey;
    private final Map<String, Object> attributes;

    private EvaluationContext(Builder builder) {
        this.userKey = Objects.requireNonNull(builder.userKey, "userKey cannot be null");
        this.attributes = Collections.unmodifiableMap(new HashMap<>(builder.attributes));
    }

    public String getUserKey() { return userKey; }
    public Object getAttribute(String key) { return attributes.get(key); }
    public Map<String, Object> getAttributes() { return attributes; }

    public static Builder builder(String userKey) {
        return new Builder(userKey);
    }

    public static final class Builder {
        private final String userKey;
        private final Map<String, Object> attributes = new HashMap<>();

        private Builder(String userKey) { this.userKey = userKey; }

        public Builder attribute(String key, String value) { attributes.put(key, value); return this; }
        public Builder attribute(String key, Number value) { attributes.put(key, value); return this; }
        public Builder attribute(String key, Boolean value) { attributes.put(key, value); return this; }
        public Builder attribute(String key, Instant value) { attributes.put(key, value.toString()); return this; }

        public EvaluationContext build() { return new EvaluationContext(this); }
    }
}
