package com.example.cameraocrtest;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import com.example.cameraocrtest.data.DocumentWord;
import com.example.cameraocrtest.data.DocumentSentence;
import com.example.cameraocrtest.tokenization.koElectraTokenizer;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KoElectraTfliteEngine {

    private final String[] unifiedLabels = {
            "O",             // 0
            "PER", "PER",   // 1, 2
            "LOC", "LOC",   // 3, 4
            "RRN", "RRN",   // 5, 6
            "EMA", "EMA",   // 7, 8
            "ID",  "ID",    // 9, 10
            "PWD", "PWD",   // 11, 12
            "ORG", "ORG",   // 13, 14
            "PHN", "PHN",   // 15, 16
            "CRD", "CRD",   // 17, 18
            "ACC", "ACC",   // 19, 20
            "PSP", "PSP",   // 21, 22
            "DLN", "DLN"    // 23, 24
    };
    private Interpreter tfliteInterpreter;
    private koElectraTokenizer tokenizer;

    // TFLite 모델 변환 시 고정한 값과 동일해야 함
    private static final int MAX_SEQ_LEN = 64;
    private int numLabels = -1; // 모델 출력 라벨 개수 (동적 할당)

    public KoElectraTfliteEngine(Context context, koElectraTokenizer tokenizer) {
        this.tokenizer = tokenizer;
        try {
            // TFLite 모델 로드
            tfliteInterpreter = new Interpreter(loadModelFile(context, "koelectra_ner_optimized.tflite"));

            // 모델이 뱉어낼 라벨(BIO 태그)의 총 개수를 모델 파일에서 직접 읽어옴
            // 출력 [1, 64, num_labels]
            int[] outputShape = tfliteInterpreter.getOutputTensor(0).shape();
            this.numLabels = outputShape[2];

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public List<DocumentWord> runInference(DocumentSentence sentence) {
        int[][] inputTokensWordIdx = tokenizer.getTokens(sentence);

        int[][] inputIds = new int[1][MAX_SEQ_LEN];
        int[][] attentionMask = new int[1][MAX_SEQ_LEN];
        int[][] tokenTypeIds = new int[1][MAX_SEQ_LEN];
        List<DocumentWord> retWordList = new ArrayList<>();
        for (int i = 0; i < MAX_SEQ_LEN; i++) {
            int tokenId = inputTokensWordIdx[i][0]; // 모델에 넣을 실제 ID만 쏙 빼냄

            inputIds[0][i] = tokenId;
            // PAD(0)가 아니면 실제 데이터이므로 1, PAD면 0
            attentionMask[0][i] = (tokenId != 0) ? 1 : 0;
            tokenTypeIds[0][i] = 0;
        }


        Object[] inputs = new Object[3];
        for (int i = 0; i < 3; i++) {
            String tensorName = tfliteInterpreter.getInputTensor(i).name();
            if (tensorName.contains("input_ids")) inputs[i] = inputIds;
            else if (tensorName.contains("attention_mask")) inputs[i] = attentionMask;
            else if (tensorName.contains("token_type_ids")) inputs[i] = tokenTypeIds;
        }


        float[][][] outputLogits = new float[1][MAX_SEQ_LEN][numLabels];
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, outputLogits);

        tfliteInterpreter.runForMultipleInputsOutputs(inputs, outputs);



        System.out.println("====== AI 추론 결과 분석 ======");
        for (int i = 0; i < MAX_SEQ_LEN; i++) {
            // 패딩 부분은 분석할 필요 없음
            if (inputIds[0][i] == 0) continue;

            float maxLogit = -Float.MAX_VALUE;
            int bestLabelId = -1;
            float sumExp = 0.0f;

            for (int j = 0; j < numLabels; j++) {
                float logit = outputLogits[0][i][j];
                sumExp += (float) Math.exp(logit);
                if (logit > maxLogit) {
                    maxLogit = logit;
                    bestLabelId = j;
                }
            }

            float probability = (float) (Math.exp(maxLogit) / sumExp) * 100.0f;

            int originalWordIdx = inputTokensWordIdx[i][1];

            //  임계치 90% 이상
            if (bestLabelId != 0 && probability >= 80.f && originalWordIdx != -1) {
                DocumentWord targetWord = sentence.getWords().get(originalWordIdx);

               // 마스킹 수행
                retWordList.add(targetWord);
                targetWord.SetWordText(unifiedLabels[bestLabelId]);

            }
        }

        return retWordList;

    }

    // assets 폴더에서 tflite 파일을 메모리로 읽어오는 헬퍼 메서드
    private MappedByteBuffer loadModelFile(Context context, String modelName) throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(modelName);
        FileInputStream is = new FileInputStream(fd.getFileDescriptor());
        FileChannel fc = is.getChannel();
        return fc.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
    }
}