package com.example.cameraocrtest.data;

public class SensitiveEntity {
    private final String value;
    private final String label;
    private final int start;
    private final int end;
    private final float confidence;

    public SensitiveEntity(String value, String label, int start, int end, float confidence) {
        this.value = value;
        this.label = label;
        this.start = start;
        this.end = end;
        this.confidence = confidence;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public float getConfidence() {
        return confidence;
    }
}
