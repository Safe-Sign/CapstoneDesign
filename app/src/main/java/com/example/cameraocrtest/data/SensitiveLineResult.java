package com.example.cameraocrtest.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SensitiveLineResult {
    private final String lineUid;
    private final String lineText;
    private final List<SensitiveEntity> entities;

    public SensitiveLineResult(String lineUid, String lineText, List<SensitiveEntity> entities) {
        this.lineUid = lineUid;
        this.lineText = lineText;
        this.entities = new ArrayList<>(entities);
    }

    public String getLineUid() {
        return lineUid;
    }

    public String getLineText() {
        return lineText;
    }

    public List<SensitiveEntity> getEntities() {
        return Collections.unmodifiableList(entities);
    }
}
