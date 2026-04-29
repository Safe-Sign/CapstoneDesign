package com.example.cameraocrtest.tokenization;

import android.content.Context;
import android.media.metrics.LogSessionId;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import com.example.cameraocrtest.data.DocumentBlock;
import com.example.cameraocrtest.data.DocumentSentence;
import com.example.cameraocrtest.data.DocumentWord;
import com.google.common.base.Ascii;

// 외부 호출 용 api
public class koElectraTokenizer {
    private final Map<String, Integer> vocabMap;

    private final FullTokenizer fullTokenizer;

    private static final int PAD_ID = 0;
    private static final int UNK_ID = 1;
    private static final int CLS_ID = 2;
    private static final int SEP_ID = 3;

    private static final int MAX_SEQ_LEN = 64; // 모델 입력 길이에 맞게 설정

    // 1. Init: 생성자에서 Vocab 로드
    // 여기서 context 는 현재 코드가 실행되고 있는 '위치'나 '환경' 그 자체 로
    // MainActivity의 Contex를 가져와야함 : 즉 MainActivity에서 호출시 this를
    // 다른 곳에서 호출시에는 MainActivity 에 getContex()를 만들어 가져와야함

    public koElectraTokenizer(Context context, String vocabFileName) {
        vocabMap = new HashMap<>();
        loadVocab(context, vocabFileName);
        fullTokenizer = new FullTokenizer(vocabMap , false);
    }

    private void loadVocab(Context context, String fileName) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(fileName)))) {
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                vocabMap.put(line.trim(), index++);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 에러 처리 로직
        }
    }

    public int[][] getTokens(DocumentSentence sentence)
    {
        int[][] inputTokensWordIdx = new int[MAX_SEQ_LEN][2];

        inputTokensWordIdx[0][0] = CLS_ID;
        inputTokensWordIdx[0][1] = -1;

        int cnt = 1;
        int wordIdx = 0;
        for(DocumentWord word : sentence.getWords())
        {
            List<String> tokens = fullTokenizer.tokenize(word.GetWordText());

            for(int i = 0; i < tokens.size(); i++)
            {
                if (cnt >= MAX_SEQ_LEN - 1) {
                    break;
                }
                inputTokensWordIdx[cnt][0] = vocabMap.getOrDefault(tokens.get(i), UNK_ID);
                inputTokensWordIdx[cnt][1] = wordIdx;
                cnt++;
            }


            if (cnt >= MAX_SEQ_LEN - 1) {
                break; // 내부 루프에서 꽉 찼으면 외부 루프도 종료
            }
            wordIdx++;
        }
        /*
        cls(classifications) -> 문장 시작 및 분류에 사용
        sep(seperator) -> 문장의 끝에 표기 되어 , 문장을 분리시킴
         */
        inputTokensWordIdx[cnt][0] = SEP_ID;
        inputTokensWordIdx[cnt][1] = -1;
        cnt++;

        while(cnt < MAX_SEQ_LEN)
        {
            inputTokensWordIdx[cnt][0] = PAD_ID;
            inputTokensWordIdx[cnt][1] = -1;
            cnt++;
        }
        return inputTokensWordIdx;
    }

    //  텍스트 처리 및 데이터 반환

    public String getTokenizationLog(List<String> tokens, int[] inputIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("================ 토큰 변환 결과 ================\n");

        for (int i = 0; i < inputIds.length; i++) {
            if (i < tokens.size()) {
                sb.append(String.format("Index [%3d] | %-15s -> ID: %d\n", i, tokens.get(i), inputIds[i]));
            } else {
                sb.append(String.format("Index [%3d] | %-15s -> ID: %d\n", i, "[PAD]", inputIds[i]));
                sb.append("... (이후 인덱스는 모두 [PAD] 0 으로 채워짐) ...\n");
                break;
            }
        }
        sb.append("--------------------------------------------------\n");
        sb.append("최종 int[] 배열 (앞 10개): [");
        int limit = Math.min(10, inputIds.length);
        for(int i = 0; i < limit; i++) {
            sb.append(inputIds[i]).append(i < limit - 1 ? ", " : "");
        }
        sb.append(inputIds.length > 10 ? ", ...]" : "]");

        return sb.toString();
    }

}
