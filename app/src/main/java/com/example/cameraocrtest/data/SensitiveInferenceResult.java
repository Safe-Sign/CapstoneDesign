package com.example.cameraocrtest.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SensitiveInferenceResult {
    private final List<SensitiveLineResult> lines;

    public SensitiveInferenceResult(List<SensitiveLineResult> lines) {
        this.lines = new ArrayList<>(lines);
    }

    public List<SensitiveLineResult> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
