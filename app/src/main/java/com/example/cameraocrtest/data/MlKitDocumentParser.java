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
    public static DocumentData paser(Text visionText)
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
            //라인 생성 후 블록에 추가 하는 반복문
            for (Text.Line mlLine : mlBlock.getLines()) {
                DocumentLine myLine = new DocumentLine(lineIndex);

                // 단어 생성 후 라인에 추가 하는 반복문
                for (Text.Element mlElement : mlLine.getElements()) {
                    String word = mlElement.getText();
                    Rect boundingBox = mlElement.getBoundingBox();

                    // 단어 생성 및 라인에 추가
                    DocumentWord myWord = new DocumentWord(blockIndex, lineIndex, word, boundingBox);
                    myLine.addWord(myWord);
                }
                myBlock.addLine(myLine);
                lineIndex++;
            }
            documentData.addBlock(myBlock);
            blockIndex++;
        }



        return  documentData;
    }


}
