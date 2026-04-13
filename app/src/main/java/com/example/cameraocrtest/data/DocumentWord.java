package com.example.cameraocrtest.data;

import android.graphics.Rect;

// 다음의 클래스에서는 단어의 데이터 형식을 정의한다.
// 단어 데이터의 경우 그 단어의 텍스트 뿐만이 아닌
// 단어가 어떤 문단에 있는지 그 중 어떤 라인에 존재하는지의 정보를 포함하고 있어야 한다
// 또한 document 단어의 경우 사진에서 추출된 단어의

// 단어 인덱스는 독립적인 상태에서도 자기자신의 인접 단어 , 라인 , 줄을 빠르게 탐색하기 위해
// 자기 자신이 속한 문단 라인 단어의 인덱스 정보를 가지고 있어야 한다.
public class DocumentWord {
    private final int blockIndex;
    private final int lineIndex;
    private final Rect boundingBox;
    private final String wordText;

    public DocumentWord(
                          int blockIndex,
                          int lineIndex,
                          String wordText,
                          Rect boundingBox
                         )
    {
        this.blockIndex = blockIndex;
        this.lineIndex = lineIndex;
        this.wordText = wordText;
        this.boundingBox = boundingBox;

    }

    // 외부에서 데이터를 읽기 위한 Getter 메서드들
    public int getBlockIndex() { return blockIndex; }
    public int getLineIndex() { return lineIndex; }
    public String getWordText() { return wordText; }
    public Rect getBoundingBox() { return boundingBox; }
}
