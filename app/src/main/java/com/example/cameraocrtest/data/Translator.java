package com.example.cameraocrtest.data;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Translator {


    private static final int MAX_CACHE_SIZE = 3;

    private static final DownloadConditions CONDITIONS =
            new DownloadConditions.Builder()
                    // .requireWifi() // 필요 시
                    .build();


    private static final Map<LangPair, com.google.mlkit.nl.translate.Translator> CACHE =
            new LinkedHashMap<LangPair, com.google.mlkit.nl.translate.Translator>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<LangPair, com.google.mlkit.nl.translate.Translator> eldest) {
                    if (size() > MAX_CACHE_SIZE) {
                        // 캐시에서 밀려나는 시점이 "더 이상 안 쓰는 시점"으로 정의됨
                        eldest.getValue().close(); // close 필요 [1](https://developer.android.com/jetpack/androidx/releases/core)
                        return true;
                    }
                    return false;
                }
            };
    private Translator() {}

    public static Task<Void> warmUp(@NonNull String sourceLang, @NonNull String targetLang) {
        com.google.mlkit.nl.translate.Translator t = getOrCreate(sourceLang, targetLang);
        // 모델 준비/다운로드
        return t.downloadModelIfNeeded(CONDITIONS);
    }

    public static Task<String> translate(@NonNull String sourceLang,
                                         @NonNull String targetLang,
                                         @NonNull String text) {
        com.google.mlkit.nl.translate.Translator t = getOrCreate(sourceLang, targetLang);
        // 주의: 모델 준비 전 translate 호출 금지 권고
        return t.translate(text);
    }

    public static void closeAll() {
        for (com.google.mlkit.nl.translate.Translator t : CACHE.values()) {
            t.close(); // close 필요 [1](https://developer.android.com/jetpack/androidx/releases/core)
        }
        CACHE.clear();
    }

    private static synchronized com.google.mlkit.nl.translate.Translator getOrCreate(String sourceLang, String targetLang) {
        LangPair key = new LangPair(sourceLang, targetLang);
        com.google.mlkit.nl.translate.Translator existing = CACHE.get(key);
        if (existing != null) return existing;

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build();

        com.google.mlkit.nl.translate.Translator created = Translation.getClient(options); // getClient로 생성 [1](https://developer.android.com/jetpack/androidx/releases/core)
        CACHE.put(key, created);
        return created;
    }

    private static final class LangPair {
        final String src;
        final String tgt;

        LangPair(String src, String tgt) {
            this.src = src;
            this.tgt = tgt;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LangPair)) return false;
            LangPair that = (LangPair) o;
            return Objects.equals(src, that.src) && Objects.equals(tgt, that.tgt);
        }

        @Override public int hashCode() {
            return Objects.hash(src, tgt);
        }
    }
}