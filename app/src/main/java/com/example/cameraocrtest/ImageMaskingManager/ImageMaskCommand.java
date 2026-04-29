package com.example.cameraocrtest.ImageMaskingManager;

//다음에서는 마스크 버퍼에서 가지고 있을 명령으로 워드 단위의 바운드 박스와
//박스 의 상태 위험 X(마스킹 X ) ,
import android.graphics.Rect;
public class ImageMaskCommand {
    Rect boundingBox;
    int color;

    ImageMaskCommand(Rect boundingBox,int color)
    {
        this.boundingBox = boundingBox;
        this.color = color;
    }
}
