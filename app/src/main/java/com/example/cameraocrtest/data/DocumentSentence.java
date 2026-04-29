package com.example.cameraocrtest.data;

import java.util.ArrayList;
import java.util.List;

public class DocumentSentence {
    private final int sentenceIndex;
    private final List<DocumentWord> words;

    public DocumentSentence(int sentenceIndex) {
        this.sentenceIndex = sentenceIndex;
        this.words = new ArrayList<>();
    }

    public void addWord(DocumentWord word) {
        this.words.add(word);
    }

    public List<DocumentWord> getWords() {
        return words;
    }

    public int getSentenceIndex() {
        return sentenceIndex;
    }

    public String getSentenceText() {
        StringBuilder sb = new StringBuilder();
        for (DocumentWord word : words) {
            sb.append(word.GetWordText()).append(" ");
        }
        return sb.toString().trim();
    }
}