package com.example.cameraocrtest.inference;

import com.example.cameraocrtest.data.DocumentBlock;
import com.example.cameraocrtest.data.DocumentData;
import com.example.cameraocrtest.data.DocumentLine;
import com.example.cameraocrtest.data.SensitiveEntity;
import com.example.cameraocrtest.data.SensitiveInferenceResult;
import com.example.cameraocrtest.data.SensitiveLineResult;
import com.example.cameraocrtest.ner.KoElectraNerEngine;

import java.util.ArrayList;
import java.util.List;

public class LineSensitiveInfoPipeline {
    private final KoElectraNerEngine nerEngine;

    public LineSensitiveInfoPipeline(KoElectraNerEngine nerEngine) {
        this.nerEngine = nerEngine;
    }

    public SensitiveInferenceResult infer(DocumentData documentData) {
        List<SensitiveLineResult> matchedLines = new ArrayList<>();
        for (DocumentBlock block : documentData.GetBlocks()) {
            for (DocumentLine line : block.GetLines()) {
                String lineText = line.GetLineText().trim();
                if (lineText.isEmpty()) {
                    continue;
                }

                List<SensitiveEntity> entities = nerEngine.inferSensitiveEntities(lineText);
                if (!entities.isEmpty()) {
                    matchedLines.add(new SensitiveLineResult(line.GetLineUid(), lineText, entities));
                }
            }
        }
        return new SensitiveInferenceResult(matchedLines);
    }
}
