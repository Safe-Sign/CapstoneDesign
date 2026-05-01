package com.example.cameraocrtest.data;

public class FieldInfo {
    private final String key;
    private final String label;
    private final boolean sensitive;
    private final String description;

    public FieldInfo(String key, String label, boolean sensitive, String description) {
        this.key = key;
        this.label = label;
        this.sensitive = sensitive;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public String getDescription() {
        return description;
    }
}
