package com.example.cameraocrtest.data;

import java.util.ArrayList;
import java.util.List;

public class DocumentBlock {
    private final int blockIndex;
    private final List<DocumentLine> lines;
    private final List<DocumentSentence> sentences;

    public DocumentBlock(int blockIndex) {
        this.blockIndex = blockIndex;
        this.lines = new ArrayList<>();
        this.sentences = new ArrayList<>();
    }

    public void AddLine(DocumentLine line) {
        this.lines.add(line);
    }

    public void addSentence(DocumentSentence sentence) {
        this.sentences.add(sentence);
    }

    public List<DocumentLine> GetLines() {
        return lines;
    }

    public List<DocumentSentence> getSentences() {
        return sentences;
    }
    public int GetBlockIndex() {
        return blockIndex;
    }

    public String GetBlockText() {
        StringBuilder sb = new StringBuilder();
        for (DocumentLine line : lines) {
            sb.append(line.GetLineText()).append("\n");
        }
        return sb.toString().trim();
    }
}
