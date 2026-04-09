package com.example.cameraocrtest.data;

import com.example.cameraocrtest.data.DocumentLine;

import java.util.ArrayList;
import java.util.List;

public class DocumentBlock {
    private final int blockIndex;
    private final List<DocumentLine> lines;

    public DocumentBlock(int blockIndex) {
        this.blockIndex = blockIndex;
        this.lines = new ArrayList<>();
    }

    public void addLine(DocumentLine line) {
        this.lines.add(line);
    }
    public List<DocumentLine> getLines() {
        return lines;
    }
    public int getBlockIndex() {
        return blockIndex;
    }

    public String getBlockText() {
        StringBuilder sb = new StringBuilder();
        for (DocumentLine line : lines) {
            sb.append(line.getLineText()).append("\n");
        }
        return sb.toString().trim();
    }
}
