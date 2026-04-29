package com.example.cameraocrtest.ImageMaskingManager;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Color;

import com.example.cameraocrtest.data.DocumentWord;
import com.example.cameraocrtest.data.DocumentSentence;

import java.util.ArrayList;
import java.util.List;

// 해당 이미지 매니저는 Document 타입을 지원하도록 제작됨
public class ImageMaskingManager {

    //여러장의 이미지를 관리하도록함
   private List<ImageMaskBuffer> imageBufferList;

   public ImageMaskingManager()
   {
       imageBufferList = new ArrayList<>();
   }
   public int addInputImage(Bitmap inputImage)
   {
        imageBufferList.add(new ImageMaskBuffer(inputImage));
        return imageBufferList.size()-1;
   }

   public int ImageCount()
   {
       return imageBufferList.size();
   }


// 이후 구조에서는 word가 page 인덱스 또한 가지고 있게 할 예정
// 위험 x 0 , 저위험 1 , 중간 위험 2 , 고위험 3 상태를 관리함

   public void DocumentWordMasking(int targetPageIndex ,int state, DocumentWord word)
   {
       if (targetPageIndex >= 0 && targetPageIndex < imageBufferList.size())
       {
            if( word != null && word.GetBoundingBox() != null && state != 0)
            {
                Rect boundBox = word.GetBoundingBox();
                int riskColor = 0;
                if(state == 1)
                {
                    //blue 저 위험
                    riskColor = Color.argb(100, 0, 0, 255);
                }
                else if(state == 2)
                {
                    //yellow 중간 위험
                    riskColor = Color.argb(100, 255, 255, 0);
                }
                else if(state == 3)
                {
                    //red 고 위험
                    riskColor = Color.argb(100, 255, 0, 0);
                }

                imageBufferList.get(targetPageIndex).commandBuffer.add(new ImageMaskCommand(boundBox,riskColor));

            }
       }

   }

   public void DocumentSentenceMasking(int targetPageIndex ,int state, DocumentSentence sentence)
   {
       for( DocumentWord word : sentence.getWords())
       {
           DocumentWordMasking(targetPageIndex ,state , word);
       }

   }


   public Bitmap GetMaskingImage(int targetPageIndex)
   {
       if (targetPageIndex < 0 || targetPageIndex >= imageBufferList.size()) return null;

       ImageMaskBuffer buffer = imageBufferList.get(targetPageIndex);
       if (buffer.orgBitMapImage == null) return null;

       // 해당 인덱스의 원본 복사본 생성
       Bitmap resultBitmap = buffer.orgBitMapImage.copy(Bitmap.Config.ARGB_8888, true);
       Canvas canvas = new Canvas(resultBitmap);

       Paint paint = new Paint();
       paint.setStyle(Paint.Style.FILL);


       for(ImageMaskCommand command : buffer.commandBuffer)
       {
           if(command == null || command.boundingBox == null) continue;
           paint.setColor(command.color);
           canvas.drawRect(command.boundingBox , paint);
       }

       return resultBitmap;

   }

    public void releaseAll() {
        for (ImageMaskBuffer buffer : imageBufferList) {
            if (buffer.orgBitMapImage != null && !buffer.orgBitMapImage.isRecycled()) {
                buffer.orgBitMapImage.recycle();
            }
            buffer.commandBuffer.clear();
        }
        imageBufferList.clear();
    }

}
