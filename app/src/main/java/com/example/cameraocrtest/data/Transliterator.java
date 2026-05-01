package com.example.cameraocrtest.data;

public final class Transliterator {
    private static final android.icu.text.Transliterator ANY_LATIN_ASCII
            = android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII");

    private Transliterator() {}

    public static String transliterate(String input) {
        return ANY_LATIN_ASCII.transliterate(input);
    }
}
