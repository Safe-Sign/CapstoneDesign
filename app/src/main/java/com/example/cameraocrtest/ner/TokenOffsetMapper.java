package com.example.cameraocrtest.ner;

import java.util.ArrayList;
import java.util.List;

public class TokenOffsetMapper {
    public static class TokenSpan {
        private final String token;
        private final int start;
        private final int end;

        public TokenSpan(String token, int start, int end) {
            this.token = token;
            this.start = start;
            this.end = end;
        }

        public String getToken() {
            return token;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }
    }

    public static List<TokenSpan> mapTokensToOffsets(String text, List<String> tokens) {
        List<TokenSpan> spans = new ArrayList<>();
        int pointer = 0;

        for (String token : tokens) {
            if ("[CLS]".equals(token) || "[SEP]".equals(token) || "[PAD]".equals(token)) {
                continue;
            }

            String normalizedToken = token.startsWith("##") ? token.substring(2) : token;
            if (normalizedToken.isEmpty()) {
                continue;
            }

            while (pointer < text.length() && Character.isWhitespace(text.charAt(pointer))) {
                pointer++;
            }

            int start = findNextMatch(text, normalizedToken, pointer);
            if (start < 0) {
                continue;
            }
            int end = start + normalizedToken.length();
            spans.add(new TokenSpan(token, start, end));
            pointer = end;
        }

        return spans;
    }

    private static int findNextMatch(String text, String token, int fromIndex) {
        int idx = text.indexOf(token, fromIndex);
        if (idx >= 0) {
            return idx;
        }
        return text.toLowerCase().indexOf(token.toLowerCase(), fromIndex);
    }
}
