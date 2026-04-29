package com.example.cameraocrtest.domain.detector;

import com.example.cameraocrtest.data.DocumentData;
import com.example.cameraocrtest.data.DocumentWord;
import com.example.cameraocrtest.domain.model.ProperNounHit;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.example.cameraocrtest.data.Translator;
import com.example.cameraocrtest.data.Transliterator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import kotlinx.coroutines.internal.ArrayQueue;

public class ProperNounDetector {
    private static class DetectionRequest {
        public final Task<String> translated;
        public final String transliterated;
        public final String origin;
        public final int sequenceNumber;
        public final DocumentWord sourceInfo;

        public DetectionRequest(Task<String> translated, String transliterated, String origin, int sequenceNumber, DocumentWord sourceInfo) {
            this.translated = translated;
            this.transliterated = transliterated;
            this.origin = origin;
            this.sequenceNumber = sequenceNumber;
            this.sourceInfo = sourceInfo;
        }
    }
    private final Queue<DetectionRequest> translationQueue;
    private final List<ProperNounHit> matchedList;
    private DocumentData input;
    int sequenceCount;
    public ProperNounDetector() {
        translationQueue = new LinkedList<>();
        matchedList = new ArrayList<>();
        Translator.warmUp(TranslateLanguage.KOREAN, TranslateLanguage.ENGLISH);
        input = null;
        sequenceCount = 0;
    }

    public void startDetection(DocumentData input) {
        this.input = input;
        sequenceCount = 0;
        for (var i : input.GetBlocks()) {
            for (var j : i.getSentences()) {
                for (var k : j.getWords()) {
                    translationQueue.add( new DetectionRequest(
                            Translator.translate(TranslateLanguage.KOREAN, TranslateLanguage.ENGLISH, k.GetWordText())
                            , Transliterator.transliterate(k.GetWordText())
                            , k.GetWordText()
                            , sequenceCount++
                            , k));
                }
            }
        }


        for (var i : translationQueue) {
            i.translated.addOnSuccessListener(result -> {
                sequenceCount--;
                // matching
                String commonSubstring = longestCommonSubstring(i.translated.getResult(), i.transliterated);
                float matchingRatio = (float)commonSubstring.length() / (float)i.transliterated.length();
                if (matchingRatio > 0.65F) {
                    matchedList.add(new ProperNounHit(i.sequenceNumber, i.origin, i.sourceInfo));
                }
            });

        }
    }

    public boolean taskDone() {
        return sequenceCount == 0;
    }

    public List<ProperNounHit> getDetectedWords() throws InterruptedException {
        if (!taskDone()) {
            for (var i : translationQueue) {
                i.translated.wait();
            }
        }
        matchedList.sort(Comparator.comparingInt(p -> p.sequenceNumber));
        return matchedList;
    }

    private static String longestCommonSubstring(String a, String b) {
        int n = a.length(), m = b.length();
        int[][] dp = new int[n + 1][m + 1];

        int maxLen = 0;
        int endIndex = 0; // a에서 끝나는 위치

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    if (dp[i][j] > maxLen) {
                        maxLen = dp[i][j];
                        endIndex = i;
                    }
                }
            }
        }

        return a.substring(endIndex - maxLen, endIndex);
    }

}
