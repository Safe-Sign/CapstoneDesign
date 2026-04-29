package com.example.cameraocrtest;

import android.graphics.Bitmap;

import com.example.cameraocrtest.data.DocumentData;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.example.cameraocrtest.data.DocumentData;
import com.example.cameraocrtest.data.MlKitDocumentParser;
public class OcrManager {

    private final TextRecognizer recognizer;

    public interface OnOcrCompleteListener {
        void onSuccess(DocumentData documentData) throws InterruptedException;
        void onError(Exception e);
    }

    public OcrManager() {
        recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
    }

    public void extractText(Bitmap bitmap, OnOcrCompleteListener listener) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        recognizer.process(image)
                .addOnSuccessListener(result -> {
                    // OCR 에서 얻어낸 데이터 그 자체를 즉 오브젝트 자체를 파서에게 전달
                    DocumentData documentData = MlKitDocumentParser.Paser(result);
                    try {
                        listener.onSuccess(documentData);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    listener.onError(e);
                });
    }
}