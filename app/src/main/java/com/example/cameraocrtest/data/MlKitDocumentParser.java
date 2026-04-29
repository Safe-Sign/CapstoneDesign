package com.example.cameraocrtest.data;

/*
* 해당 파서는 google ML kit의 종속성을 가진 Parser 임
*
* 현재는 그저 google ML kit 의 OCR 만을 활용하여 얻은 결과에 대해
* Document 내부 데이터 형식을 맞추어 주어 필요한 정보만을 추출하여 내부 계층 규칙에 맞추어 구조화하여
* 넘겨주는 역할만을 수행하게 하는 정도로만 함
*
* (추가 , SHS 에게 ) 이후에 ML kit 의 OCR 뿐만이 아닌 엔티티 탐색등의 추가 기능이
* 들어가는 경우 그 규모가 커짐에 따라 무조건 패키지 분리시켜서 따로 관리
*
* 사실상 기존의 파서(데이터의 추출 전달 역할만) 에서
* 데이터 를
* 추출 ->
* 가공(사실상 텍스트 마스킹 , 마스킹 된 정보는 이후 이미지 처리를 위해 이미지 수정 데이터 셋으로 따로 뽑을 필요성 존재)->
* 저장 하는 기능을 수행하는 마스킹 기능으로 확장 가능 할 수 도..
*
* */

import android.graphics.Rect;
import com.google.mlkit.vision.text.Text;

public class MlKitDocumentParser {

    //여기서의 Text 는 정말 그 일반적인 텍스트가 아닌 mlkit vision 에서 채득된 정보가 모두 담긴 객체임
    // 근데 왜 Text 라는 이름이냐 -> 나도 몰러유..
    public static DocumentData Paser(Text visionText)
    {
        // 이친구에게 데이터를 추출해서 넣고 리턴 시킴
        DocumentData documentData = new DocumentData();

        if(visionText == null)
            return documentData;

        int blockIndex = 0;
        //문단 생성후 데이터에 추가 하는 반복문
        for (Text.TextBlock mlBlock : visionText.getTextBlocks()) {
            DocumentBlock myBlock = new DocumentBlock(blockIndex);

            int lineIndex = 0;
            int sentenceIndex = 0;

            //라인 생성 후 블록에 추가 하는 반복문
            for (Text.Line mlLine : mlBlock.getLines()) {
                DocumentLine myLine = new DocumentLine(blockIndex, lineIndex);

                // 단어 생성 후 라인에 추가 하는 반복문
                for (Text.Element mlElement : mlLine.getElements()) {
                    String word = mlElement.getText();
                    Rect boundingBox = mlElement.getBoundingBox();

                    // 단어 생성 및 라인에 추가
                    DocumentWord myWord = new DocumentWord(blockIndex, lineIndex, word, boundingBox);
                    myLine.AddWord(myWord);
                }
                myBlock.AddLine(myLine);
                lineIndex++;
            }
            documentData.AddBlock(myBlock);
            DocumentSentence tempSentence = new DocumentSentence(sentenceIndex);
            boolean senteneceEmpty = true;

            for(DocumentLine parsedLine : myBlock.GetLines())
            {
                for(DocumentWord parsedWord : parsedLine.getWords())
                {
                    tempSentence.addWord(parsedWord);
                    parsedWord.SetSentenceIndex(sentenceIndex);
                    senteneceEmpty = false;

                    //문장 마지막 단어인지 검사용
                    String wordText = parsedWord.GetWordText();

                    //먼저 1. A. 가. 와 같이 조항 번호로 사용될수있는 한글자. 의 경우
                    //. 이 발견되더라도 문장의 끝으로 보지 않는다.
                    boolean isListIndex = wordText.matches("^([0-9]{1,2}|[a-zA-Z]|[가-힣])\\.$");

                    if(!isListIndex)
                    {
                        //근로계약서에서 주로 사용되는 표현에서 검출시 문장의 끝으로
                        boolean lastWord = wordText.endsWith("다.") ||
                                   wordText.endsWith("함.") ||
                                   wordText.endsWith("됨.") ||
                                   wordText.endsWith("임.");

                        // 위에서 검출되지 않은 경우에도 만일 두 단어 이상에 마지막이 .이라면 문장 마지막으로 간주함
                        lastWord = lastWord || (wordText.endsWith(".") && tempSentence.getWords().size() >= 2);

                        if(lastWord)
                        {
                            //해당 단어가 문장 마지막인 경우
                            myBlock.addSentence(tempSentence);
                            sentenceIndex++;
                            //다음 문장 위해 초기화
                            tempSentence = new DocumentSentence(sentenceIndex);
                            senteneceEmpty = true;
                        }
                    }

                }
            }

            //문장에 단어가 있는데(비어있지 않은데) 문장이 안끝난 경우
            //그냥 해단 단어 조합을 한문장으로 닫어줌
            if(!senteneceEmpty)
            {
                myBlock.addSentence(tempSentence);

            }



            blockIndex++;
        }



        return  documentData;
    }


}
