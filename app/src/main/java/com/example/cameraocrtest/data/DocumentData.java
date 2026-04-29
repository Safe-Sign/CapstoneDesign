package com.example.cameraocrtest.data;

// 다음 DocumentInputData 객체는
// 이미지 영상에서의 추출된 글자와 그  좌표를 및 바운딩 박스를 담아내는 데이터를 집합을 정의 시킴
// 추출된 글자는 문단 라인 단어라는 계층적인 형태를 띄며
// 단어는 바로 문단 , 라인의 인덱스를 반환할 수 있어야함
// 라인은 바로 문단 인덱스를 반환할 수 있어야함
//
// 또한 DocumentData 와 그 하위 데이터는 ML kit 종속되지 않는 형태로
// DocumentMLKitDataParser 가 OcrManager로 부터 결과를 받아
// DocumentData 를 형성해 내어줌


import java.util.ArrayList;
import java.util.List;

public class DocumentData {
    private final List<DocumentBlock> blocks;

    public DocumentData() {
        this.blocks = new ArrayList<>();
    }

    public void AddBlock(DocumentBlock block) {
        this.blocks.add(block);
    }

    public List<DocumentBlock> GetBlocks() {
        return blocks;
    }

    public String GetFullText() {
        StringBuilder sb = new StringBuilder();
        for (DocumentBlock block : blocks) {
            // 문단 구분을 위해 줄 바꿈 2번 함
            sb.append(block.GetBlockText()).append("\n\n");
        }
        return sb.toString().trim();
    }
}