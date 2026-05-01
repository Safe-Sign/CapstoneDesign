package com.example.cameraocrtest.ImageMaskingManager;

import android.graphics.Bitmap;
import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;

// 해당 모듈에서는 이미지 1장의 비트맵과 해당 이미지에 들어갈 마스킹 명령 집합 관리

public class ImageMaskBuffer {
    Bitmap orgBitMapImage;
    List<ImageMaskCommand> commandBuffer = new ArrayList<>();

    ImageMaskBuffer(Bitmap inputImage)
    {
        orgBitMapImage = inputImage;
    }

    void AddCommand(Rect boundingBox,int color)
    {
        ImageMaskCommand command = new ImageMaskCommand(boundingBox,color);
        commandBuffer.add(command);
    }

}
