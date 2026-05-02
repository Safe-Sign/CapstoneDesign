package com.example.cameraocrtest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.example.cameraocrtest.data.NetworkClient;
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
import com.example.cameraocrtest.data.ResponseData;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
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
        KOELECTRA_AND_REGEX_MASKING, // PROPER_NOUN_AND_REGEX_MAKSING,
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
                            int diffSum = 0;
                            for (var k : regexResult) {
                                if (k.getConfidence() > 0.75F) {
                                    String pre = result.substring(0, k.getStart() - diffSum);
                                    String post = result.substring(k.getEnd() - diffSum);
                                    result = pre + "[" + k.getLabel() + "]" + post;
                                }
                                diffSum += k.getEnd() - k.getStart() - k.getLabel().length() - 2;
                            }

                            // Append json request content
                            sentenceField.put(new JSONObject()
                                    .put("block_id", blockIdx)
                                    .put("sentence_id", sentenceIdx)
                                    .put("text", result));
                        }
                    }
                    jsonObj.put("sentences", sentenceField);
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
                case KOELECTRA_AND_REGEX_MASKING: {
                    flag = MaskingMethod.KOELECTRA_NER_MASKING;
                    shouldRun -= 2;
                }
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

                            StringBuilder temp =  new StringBuilder();
                            // ProperNounCheck
                            properNounDetector.startDetection(documentData
                                    , new ProperNounDetector.OnDetectionCompleteListener() {
                                        @Override
                                        public void onComplete(List<ProperNounHit> result) throws JSONException {
                                            JSONObject request = createJsonRequest(documentData, result, MaskingMethod.KOELECTRA_AND_REGEX_MASKING);
                                            fullLogBuilder.append(request.toString());
                                            temp.append(request.toString());

                                            String url = "https://YOUR_SERVER_HOST/api/analyze"; // TODO: 서버 주소로 변경
                                            String json = request.toString();

                                            NetworkClient networkClient = new NetworkClient();
                                            networkClient.postJson(url, json, new NetworkClient.ApiCallback() {
                                                @Override
                                                public void onSuccess(String body) {
                                                    // ✅ 서버 응답 JSON 파싱 후 UI 업데이트
                                                    List<ResponseData> responseDataList = parseServerResponse(body);

                                                    runOnUiThread(() -> {
                                                        endUserInterface(responseDataList, documentData, 0, json);
                                                    });
                                                }

                                                @Override
                                                public void onFailure(Exception e) {
                                                    runOnUiThread(() -> {
                                                        tvOcrResult.setText("서버 요청 실패: " + e.getMessage());
                                                        updateUIState(UIState.RESULT);
                                                    });
                                                }
                                            });
//                                              //test
//                                            String dummyServerResponseJson = "{\n" +
//                                                    "  \"results\": [\n" +
//                                                    "    {\n" +
//                                                    "      \"block_id\": 0,\n" +
//                                                    "      \"sentence_id\": 0,\n" +
//                                                    "      \"state\": 0,\n" +
//                                                    "      \"reason\": \"정상 조항입니다.\",\n" +
//                                                    "      \"law\": \"\",\n" +
//                                                    "      \"action\": \"이상 없음\"\n" +
//                                                    "    },\n" +
//                                                    "    {\n" +
//                                                    "      \"block_id\": 0,\n" +
//                                                    "      \"sentence_id\": 1,\n" +
//                                                    "      \"state\": 0,\n" +
//                                                    "      \"reason\": \"정상 조항입니다.\",\n" +
//                                                    "      \"law\": \"\",\n" +
//                                                    "      \"action\": \"이상 없음\"\n" +
//                                                    "    },\n" +
//                                                    "    {\n" +
//                                                    "      \"block_id\": 1,\n" +
//                                                    "      \"sentence_id\": 0,\n" +
//                                                    "      \"state\": 0,\n" +
//                                                    "      \"reason\": \"정상 조항입니다.\",\n" +
//                                                    "      \"law\": \"\",\n" +
//                                                    "      \"action\": \"이상 없음\"\n" +
//                                                    "    },\n" +
//                                                    "    {\n" +
//                                                    "      \"block_id\": 1,\n" +
//                                                    "      \"sentence_id\": 1,\n" +
//                                                    "      \"state\": 2,\n" +
//                                                    "      \"reason\": \"건강 상태 정보 수집\",\n" +
//                                                    "      \"law\": \"개인정보보호법 제20조\",\n" +
//                                                    "      \"action\": \"수정 권장\"\n" +
//                                                    "    },\n" +
//                                                    "    {\n" +
//                                                    "      \"block_id\": 2,\n" +
//                                                    "      \"sentence_id\": 0,\n" +
//                                                    "      \"state\": 2,\n" +
//                                                    "      \"reason\": \"종교 정보 수집\",\n" +
//                                                    "      \"law\": \"개인정보보호법 제21조\",\n" +
//                                                    "      \"action\": \"수정 권장\"\n" +
//                                                    "    },\n" +
//                                                    "    {\n" +
//                                                    "      \"block_id\": 2,\n" +
//                                                    "      \"sentence_id\": 1,\n" +
//                                                    "      \"state\": 3,\n" +
//                                                    "      \"reason\": \"사회적 신분(노조 활동 이력) 정보 수집\",\n" +
//                                                    "      \"law\": \"개인정보보호법 제22조\",\n" +
//                                                    "      \"action\": \"수정 요청\"\n" +
//                                                    "    },\n" +
//                                                    "    {\n" +
//                                                    "      \"block_id\": 2,\n" +
//                                                    "      \"sentence_id\": 2,\n" +
//                                                    "      \"state\": 2,\n" +
//                                                    "      \"reason\": \"병역 사항 수집\",\n" +
//                                                    "      \"law\": \"개인정보보호법 제23조\",\n" +
//                                                    "      \"action\": \"수정 권장\"\n" +
//                                                    "    }\n" +
//                                                    "  ]\n" +
//                                                    "}";
//                                            List<ResponseData> responseDataList = parseServerResponse(dummyServerResponseJson);
//
//                                            endUserInterface(responseDataList, documentData, 0,fullLogBuilder.toString());
                                            // 5. 누적된 전체 로그 텍스트를 화면에 띄우기
//                                            runOnUiThread(() -> {
//                                                tvOcrResult.setText(fullLogBuilder.toString());
//                                                updateUIState(UIState.RESULT);
//                                            });
                                        }
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

    //현재 05.02일자 테스트 기준으로 pageIndex 는 0으로 고정(즉 page 1장 짜리에 대해서만 고려함)
    @SuppressLint("ClickableViewAccessibility")
    private void endUserInterface(List<ResponseData> responseDataList , DocumentData documentData, int pageIndex , String requestJsonString)
    {

         List<DocumentSentence> dangerSentenceList = new ArrayList<>();
                 List<ResponseData> dangerResponseDataList = new ArrayList<>();

        for(ResponseData data : responseDataList)
        {
            if(data.state > 0)
            {
                DocumentSentence sentence = documentData.GetBlocks().get(data.blockIdx).getSentences().get(data.sentenceIdx);

                dangerSentenceList.add(sentence);
                dangerResponseDataList.add(data);

                imageMaskingManager.DocumentSentenceMasking(pageIndex,data.state,sentence);



            }

        }


        Bitmap outImage = imageMaskingManager.GetMaskingImage(pageIndex);
        runOnUiThread(() -> {
            if (outImage != null) {
                ivMaskedResult.setImageBitmap(outImage);
            }
            updateUIState(UIState.RESULT);

            ivMaskedResult.setOnTouchListener( (v, event) -> {
                // 손가락이 닿는 순간(ACTION_DOWN)만 처리
                if (event.getAction() == MotionEvent.ACTION_DOWN) {

                    // 화면 터치 좌표를 실제 비트맵 이미지 좌표로 역산
                    float[] touchPoint = new float[] {event.getX(), event.getY()};
                    Matrix inverseMatrix = new Matrix();
                    ivMaskedResult.getImageMatrix().invert(inverseMatrix);
                    inverseMatrix.mapPoints(touchPoint);

                    boolean isTouchedDangerArea = false;

                    // 캡처해둔 위험 조항(Sentence) 리스트를 순회
                    for (int i = 0; i < dangerSentenceList.size(); i++) {
                        DocumentSentence sentence = dangerSentenceList.get(i);
                        boolean sentenceTouched = false;

                        // 해당 문장의 '단어(Word)' 바운딩 박스를 하나씩 검사
                        // (※ 사용하는 OCR 라이브러리에 따라 getWords() 메서드명은 다를 수 있습니다)
                        if (sentence.getWords() != null) {
                            for (DocumentWord word : sentence.getWords()) {
                                Rect wordBox = word.GetBoundingBox();

                                // 단어 사각형 안에 터치 좌표가 포함되면 즉시 충돌 판정
                                if (wordBox != null && wordBox.contains((int) touchPoint[0], (int) touchPoint[1])) {
                                    sentenceTouched = true;
                                    break; // 단어 탐색 루프 탈출
                                }
                            }
                        }

                        // 문장이 터치된 것으로 확인되면 정보 띄우기
                        if (sentenceTouched) {
                            ResponseData matchedData = dangerResponseDataList.get(i);
                            // 화면 하단에 서버 정보 표기
                            showDetailInfoToBottom(matchedData.reason, matchedData.law, matchedData.action);

                            isTouchedDangerArea = true;
                            break; // 다른 문장은 더 찾을 필요 없으므로 전체 루프 탈출
                        }
                    }

                    // 위험 조항이 아닌 여백을 터치했다면 띄워둔 정보창 숨기기
                    if (!isTouchedDangerArea) {
                        hideDetailInfoFromBottom();
                    }
                }
                return true; // 이벤트를 소비함 (다른 터치 이벤트로 전파 방지)
            });


            Button logButton = findViewById(R.id.btn_log_view);

            if (logButton != null) {
                // 응답이 성공적으로 와서 마스킹이 끝났으므로 버튼을 활성화/노출합니다.
                logButton.setVisibility(View.VISIBLE);

                // 버튼 클릭 시 다이얼로그를 띄우는 이벤트를 달아줍니다.
                logButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showLogDialog(requestJsonString);
                    }
                });
            }
        });




    }
    private BottomSheetDialog currentBottomSheet;
    private void showDetailInfoToBottom(String reason, String law, String action) {
        // 이미 띄워진 바텀 시트가 있다면 닫기
        if (currentBottomSheet != null && currentBottomSheet.isShowing()) {
            currentBottomSheet.dismiss();
        }

        currentBottomSheet = new BottomSheetDialog(this);

        // (이전에 로그 뷰 띄울 때처럼 코드로 동적 뷰 생성, 혹은 XML을 inflate 해도 됨)
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        TextView tvInfo = new TextView(this);
        tvInfo.setText("사유: " + reason + "\n\n법령: " + law + "\n\n조치: " + action);
        tvInfo.setTextSize(16f);

        layout.addView(tvInfo);

        currentBottomSheet.setContentView(layout);
        currentBottomSheet.show();
    }

    private void hideDetailInfoFromBottom() {
        // 바텀 시트는 사용자가 바깥을 누르면 알아서 닫히지만,
        // 여백 터치 시 강제로 닫고 싶다면 아래 코드 사용
        if (currentBottomSheet != null && currentBottomSheet.isShowing()) {
            currentBottomSheet.dismiss();
        }
    }

    // JSON 텍스트를 스크롤 가능한 다이얼로그로 띄워주는 메서드
    private void showLogDialog(String jsonLog) {
        // 1. 다이얼로그에 들어갈 스크롤 뷰와 텍스트 뷰 동적 생성
        ScrollView scrollView = new ScrollView(this); // Fragment라면 requireContext() 사용
        TextView textView = new TextView(this);

        // 2. 텍스트 뷰 세팅 (여백, 글자크기, 내용)
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        textView.setPadding(padding, padding, padding, padding);
        textView.setTextSize(14f);
        textView.setText(jsonLog);

        scrollView.addView(textView);

        // 3. AlertDialog 생성 및 표시
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("서버 전송 로그 (Request JSON)")
                .setView(scrollView) // 스크롤 뷰 장착
                .setPositiveButton("닫기", null)
                .show();
    }


    // 서버에서 받은 JSON 문자열을 List<ResponseData>로 변환하는 함수
    private List<ResponseData> parseServerResponse(String jsonString) {
        List<ResponseData> responseDataList = new ArrayList<>();

        try {
            // 1. 최상위 JSON 객체 생성
            JSONObject rootObject = new JSONObject(jsonString);

            // 2. "results" 배열 꺼내기
            JSONArray resultsArray = rootObject.getJSONArray("results");

            // 3. 배열을 순회하며 ResponseData 객체로 만들기
            for (int i = 0; i < resultsArray.length(); i++) {
                JSONObject item = resultsArray.getJSONObject(i);


                int blockIdx = item.getInt("block_id");
                int sentenceIdx = item.getInt("sentence_id");

                // 나머지 데이터는 그대로 꺼냅니다.
                int state = item.getInt("state");
                String reason = item.getString("reason");
                String law = item.getString("law");
                String action = item.getString("action");

                // 객체를 생성하여 리스트에 추가
                ResponseData data = new ResponseData(blockIdx, sentenceIdx, state, reason, law, action);
                responseDataList.add(data);
            }

        } catch (JSONException e) {
            // JSON 형식이 잘못되었거나 키 값이 없을 때의 예외 처리
            e.printStackTrace();
        }

        return responseDataList;
    }

}
