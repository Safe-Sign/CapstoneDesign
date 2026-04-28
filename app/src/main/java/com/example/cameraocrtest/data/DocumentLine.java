package com.example.cameraocrtest.data;

import com.example.cameraocrtest.data.DocumentWord;

import java.util.ArrayList;
import java.util.List;

//라인 생성시에는 해당라인은 비어있는 상태이고
//파서가 OCR 데이터로부터
// 문단 내 라인 번호 생성시 넣어주고
// 다음 라인 전까지의 단어는 addWord를 통해 추가하는 형태
public class DocumentLine {
    private final int lineIndex;

    private final List<DocumentWord> words;

    public DocumentLine(int lineIndex)
    {
        this.lineIndex = lineIndex;
        this.words = new ArrayList<>();
    }

    public void AddWord(DocumentWord word)
    {
        this.words.add(word);
    }
    public List<DocumentWord> getWords() {
        return words;
    }
    public int GetLineIndex() {
        return lineIndex;
    }

    public String GetLineText() {
        StringBuilder sb = new StringBuilder();
        for (DocumentWord word : words) {
            sb.append(word.GetWordText()).append(" ");
        }
        // 마지막 띄어쓰기 1개만 제거하고 반환
        return sb.toString().trim();
    }

}
