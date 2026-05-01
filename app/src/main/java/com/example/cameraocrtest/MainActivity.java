package com.example.cameraocrtest;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.cameraocrtest.ImageMaskingManager.ImageMaskingManager;
import com.example.cameraocrtest.data.DocumentData;
import com.example.cameraocrtest.data.DocumentBlock;
import com.example.cameraocrtest.data.DocumentSentence;
import com.example.cameraocrtest.data.DocumentWord;
import com.example.cameraocrtest.domain.detector.ProperNounDetector;
import com.example.cameraocrtest.domain.model.ProperNounHit;
import com.example.cameraocrtest.data.FieldInfo;
import com.example.cameraocrtest.data.SensitiveEntity;
import com.example.cameraocrtest.data.SensitiveInferenceResult;
import com.example.cameraocrtest.data.SensitiveLineResult;
import com.example.cameraocrtest.inference.LineSensitiveInfoPipeline;
import com.example.cameraocrtest.ner.RegexNerEngine;
import com.example.cameraocrtest.parser.FieldInfoJsonParser;
import com.example.cameraocrtest.tokenization.koElectraTokenizer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;

import java.util.List;
import java.util.Set;

import kotlin.text.Regex;

public class MainActivity extends AppCompatActivity {
    private TextView tvHeaderStatus;
    private PreviewView viewFinder;
    private ScrollView scrollViewResult;
    private TextView tvOcrResult;
    private Button btnCapture;
    private Button btnBackToCamera;

    // Core Managers
    private CameraManager cameraManager;
    private OcrManager ocrManager;

    KoElectraTfliteEngine koElectraEngine;
    private koElectraTokenizer tokenizer;
    private LineSensitiveInfoPipeline lineSensitiveInfoPipeline;

    private ImageView ivMaskedResult;
    private ImageMaskingManager imageMaskingManager;


    // ProperNounDetection
    private ProperNounDetector properNounDetector;

    private RegexNerEngine regexNerEngine;

    // 권한 요청 런처
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    cameraManager.startCamera(viewFinder);
                } else {
                    Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initManagers();
        setupListeners();

        checkCameraPermission();
    }

    private void initViews() {
        tvHeaderStatus = findViewById(R.id.tvHeaderStatus);
        viewFinder = findViewById(R.id.viewFinder);
        scrollViewResult = findViewById(R.id.scrollViewResult);
        tvOcrResult = findViewById(R.id.tvOcrResult);
        btnCapture = findViewById(R.id.btnCapture);
        btnBackToCamera = findViewById(R.id.btnBackToCamera);
        ivMaskedResult = findViewById(R.id.ivMaskedResult);

    }

    private void initManagers() {
        cameraManager = new CameraManager(this, this);
        ocrManager = new OcrManager();
        // 앱 시작 시 한 번만 초기화 (assets/vocab.txt 참조)
        tokenizer = new koElectraTokenizer(this, "vocab.txt");
        properNounDetector = new ProperNounDetector();
        List<FieldInfo> fieldInfos = FieldInfoJsonParser.loadFromAsset(this, "field_info.json");
        Set<String> sensitiveTags = FieldInfoJsonParser.buildSensitiveTagSet(fieldInfos);
        lineSensitiveInfoPipeline = new LineSensitiveInfoPipeline(new RegexNerEngine(tokenizer, sensitiveTags));
        imageMaskingManager = new ImageMaskingManager();

        koElectraEngine = new KoElectraTfliteEngine(this, tokenizer);
        regexNerEngine = new RegexNerEngine(tokenizer);

    }

    private enum MaskingMethod {
        PROPER_NOUN_MASKING,
        KOELECTRA_NER_MASKING,
        REGEX_NER_MASKIING,
        PROPER_NOUN_AND_KOELECTRA_MASKING,
        // PROPER_NOUN_AND_REGEX_MAKSING,
        PROPER_NOUN_AND_KOELECTRA_AND_REGEX_MASKING
    }
    private JSONObject createJsonRequest(DocumentData documentData, List<ProperNounHit> properNounHits, MaskingMethod flag) throws JSONException {
        JSONObject jsonObj = new JSONObject();
        JSONArray sentenceField = new JSONArray();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            jsonObj.append("session_id", "test02");
            jsonObj.append("filename", "test_contract.jpg");
        }


        for (int shouldRun = 0; shouldRun < 1; ++shouldRun) {
            switch (flag) {
                case PROPER_NOUN_MASKING: {
                    for (var i : properNounHits) {
                        i.sourceInfo.SetWordText("*".repeat(i.origin.length()));
                    }
                    break;
                }
                case KOELECTRA_NER_MASKING: {
                    for (var i : documentData.GetBlocks()) {
                        for (var j : i.getSentences()) {
                            String text = j.getSentenceText().trim();
                            if (text.isEmpty()) {
                                continue;
                            }
                            koElectraEngine.runInference(j);
                        }
                    }
                    break;
                }
                case REGEX_NER_MASKIING: {
                    for (var i : documentData.GetBlocks()) {
                        int blockIdx = i.GetBlockIndex();
                        for (var j : i.getSentences()) {
                            int sentenceIdx = j.getSentenceIndex();
                            String result = j.getSentenceText();
                            List<SensitiveEntity> regexResult = regexNerEngine.inferSensitiveEntities(result);

                            for (var k : regexResult) {
                                if (k.getConfidence() > 0.75F) {
                                    String pre = result.substring(0, k.getStart());
                                    String post = result.substring(k.getEnd());
                                    result = pre + "[" + k.getLabel() + "]" + post;
                                }
                            }

                            // Append json request content
                            sentenceField.put(new JSONObject()
                                    .put("block_id", blockIdx)
                                    .put("sentence_id", sentenceIdx)
                                    .put("text", result));
                        }
                    }
                    return jsonObj;
                }
                case PROPER_NOUN_AND_KOELECTRA_MASKING: {
                    int blockIdxBack = -1;
                    int sentenceIdxBack = -1;
                    for (var i : properNounHits) {
                        int blockIdx = i.sourceInfo.GetBlockIndex();
                        int sentenceIdx = i.sourceInfo.GetSentenceIndex();
                        DocumentSentence sentence = documentData.GetBlocks().get(blockIdx).getSentences().get(sentenceIdx);

                        if (blockIdxBack == blockIdx && sentenceIdxBack == sentenceIdx) {
                            continue;
                        }
                        blockIdxBack = blockIdx;
                        sentenceIdxBack = sentenceIdx;

                        String sentenceText = sentence.getSentenceText().trim();

                        if (sentenceText.isEmpty()) {
                            continue;
                        }

                        koElectraEngine.runInference(sentence);
                    }
                    flag = MaskingMethod.REGEX_NER_MASKIING;
                    break;
                }
/*
                case PROPER_NOUN_AND_REGEX_MAKSING: {

                    return jsonObj;
                }
 */
                case PROPER_NOUN_AND_KOELECTRA_AND_REGEX_MASKING: {
                    flag = MaskingMethod.PROPER_NOUN_AND_KOELECTRA_MASKING;
                    shouldRun -= 2;
                }
                    break;
                default:
                    break;
            }
        }
        for (var i : documentData.GetBlocks()) {
            int blockIdx = i.GetBlockIndex();
            for (var j : i.getSentences()) {
                int sentenceIdx = j.getSentenceIndex();
                sentenceField.put(new JSONObject()
                        .put("block_id", blockIdx)
                        .put("sentence_id", sentenceIdx)
                        .put("text", j.getSentenceText()));
            }
        }
        // Write JSON request content
        jsonObj.put("sentences", sentenceField);
        return jsonObj;
    }

    private void setupListeners() {

        btnCapture.setOnClickListener(v -> {
            updateUIState(UIState.PROCESSING);

            // 1. 비동기 사진 촬영 요청
            cameraManager.takePicture(new CameraManager.OnPictureTakenListener() {
                @Override
                public void onSuccess(Bitmap bitmap) {
                    //사진 찍은 이미지 마스킹 manager 에 전달
                    imageMaskingManager.addInputImage(bitmap);
                    // 2 촬영된 Bitmap을 그대로 OCR 분석에 전달
                    ocrManager.extractText(bitmap, new OcrManager.OnOcrCompleteListener() {
                        @Override
                        public void onSuccess(DocumentData documentData) throws InterruptedException {
                            if (documentData.GetBlocks().isEmpty()) {
                                runOnUiThread(() -> {
                                    tvOcrResult.setText("텍스트를 인식할 수 없습니다.");
                                    updateUIState(UIState.RESULT);
                                });
                                return;
                            }

                            StringBuilder fullLogBuilder = new StringBuilder();
                            fullLogBuilder.append("원본\n");
                            fullLogBuilder.append(documentData.GetFullText());

                            // ProperNounCheck
                            properNounDetector.startDetection(documentData
                                    , new ProperNounDetector.OnDetectionCompleteListener() {
                                        @Override
                                        public void onComplete(List<ProperNounHit> result) throws JSONException {
                                            JSONObject request = createJsonRequest(documentData, result, MaskingMethod.PROPER_NOUN_AND_KOELECTRA_AND_REGEX_MASKING);
                                            fullLogBuilder.append(request.toString());
                                            // 5. 누적된 전체 로그 텍스트를 화면에 띄우기
                                            runOnUiThread(() -> {
                                                tvOcrResult.setText(fullLogBuilder.toString());
                                                updateUIState(UIState.RESULT);
                                            });
                                        }
                                    });

                            SensitiveInferenceResult sensitiveResult = lineSensitiveInfoPipeline.infer(documentData);
                            fullLogBuilder.append("민감정보 추론 결과\n");
                            fullLogBuilder.append(formatSensitiveResult(sensitiveResult));

                            Bitmap outImage = imageMaskingManager.GetMaskingImage(0);
                            // 5. 누적된 전체 로그 텍스트를 화면에 띄우기
                            runOnUiThread(() -> {
                                if (outImage != null) {
                                    ivMaskedResult.setImageBitmap(outImage);
                                }
                                tvOcrResult.setText(fullLogBuilder.toString());
                                updateUIState(UIState.RESULT);
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            runOnUiThread(() -> {
                                tvOcrResult.setText("OCR 분석 중 오류가 발생했습니다: " + e.getLocalizedMessage());
                                updateUIState(UIState.RESULT);
                            });
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        updateUIState(UIState.CAMERA);
                        Toast.makeText(MainActivity.this, "촬영 실패", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        // 결과 화면에서 다시 카메라로 돌아가는 버튼
        btnBackToCamera.setOnClickListener(v -> {

            imageMaskingManager.PopImageBufferList();
            updateUIState(UIState.CAMERA);
        });
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraManager.startCamera(viewFinder);
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void updateUIState(UIState state) {
        switch (state) {
            case CAMERA:
                tvHeaderStatus.setText("사진 촬영");
                viewFinder.setVisibility(View.VISIBLE);
                scrollViewResult.setVisibility(View.GONE);
                btnCapture.setVisibility(View.VISIBLE);
                btnBackToCamera.setVisibility(View.GONE);
                break;

            case RESULT:
                tvHeaderStatus.setText("텍스트 변환 완료");
                viewFinder.setVisibility(View.GONE);
                scrollViewResult.setVisibility(View.VISIBLE);
                btnCapture.setVisibility(View.GONE);
                btnBackToCamera.setVisibility(View.VISIBLE);
                break;

            case PROCESSING:
                tvHeaderStatus.setText("처리 중...");
                btnCapture.setVisibility(View.GONE);
                btnBackToCamera.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraManager != null) {
            cameraManager.shutDown();
        }
    }

    // 상태에서 CROP 제거
    private enum UIState {
        CAMERA, RESULT, PROCESSING
    }

    private String formatSensitiveResult(SensitiveInferenceResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"detectedLineCount\": ").append(result.getLines().size()).append(",\n");
        sb.append("  \"lines\": [\n");

        for (int i = 0; i < result.getLines().size(); i++) {
            SensitiveLineResult lineResult = result.getLines().get(i);
            sb.append("    {\n");
            sb.append("      \"lineUid\": \"").append(escapeJson(lineResult.getLineUid())).append("\",\n");
            sb.append("      \"lineText\": \"").append(escapeJson(lineResult.getLineText())).append("\",\n");
            sb.append("      \"entities\": [\n");

            for (int j = 0; j < lineResult.getEntities().size(); j++) {
                SensitiveEntity entity = lineResult.getEntities().get(j);
                sb.append("        {\n");
                sb.append("          \"label\": \"").append(escapeJson(entity.getLabel())).append("\",\n");
                sb.append("          \"value\": \"").append(escapeJson(entity.getValue())).append("\",\n");
                sb.append("          \"start\": ").append(entity.getStart()).append(",\n");
                sb.append("          \"end\": ").append(entity.getEnd()).append(",\n");
                sb.append("          \"confidence\": ").append(String.format(java.util.Locale.US, "%.4f", entity.getConfidence())).append("\n");
                sb.append("        }");
                if (j < lineResult.getEntities().size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }

            sb.append("      ]\n");
            sb.append("    }");
            if (i < result.getLines().size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
