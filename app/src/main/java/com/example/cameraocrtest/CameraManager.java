package com.example.cameraocrtest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraManager {

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private ImageCapture imageCapture;

    // 무거운 이미지 처리(Crop 등)를 위한 백그라운드 스레드 풀
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    public interface OnPictureTakenListener {
        void onSuccess(Bitmap bitmap);
        void onError(Exception e);
    }

    public interface OnImageCroppedListener {
        void onCropped(Bitmap bitmap);
    }

    public CameraManager(Context context, LifecycleOwner lifecycleOwner) {
        this.context = context;
        this.lifecycleOwner = lifecycleOwner;
    }

    /**
     * CameraX 프리뷰를 지정된 PreviewView에 바인딩합니다.
     */
    public void startCamera(PreviewView previewView) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture);
            } catch (Exception exc) {
                Log.e("CameraManager", "Use case binding failed", exc);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    /**
     * 비동기로 사진을 촬영하고 콜백을 통해 Bitmap을 반환합니다.
     */
    public void takePicture(OnPictureTakenListener listener) {
        if (imageCapture == null) {
            listener.onError(new IllegalStateException("ImageCapture is not initialized"));
            return;
        }

        imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        Bitmap bitmap = imageProxyToBitmap(image);
                        Bitmap rotatedBitmap = rotateBitmap(bitmap, image.getImageInfo().getRotationDegrees());
                        image.close();
                        listener.onSuccess(rotatedBitmap);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("CameraManager", "Photo capture failed: " + exception.getMessage(), exception);
                        listener.onError(exception);
                    }
                }
        );
    }

    /**
     * 사용자가 지정한 사각형 영역(Rect)을 기준으로 원본 Bitmap을 잘라냅니다.
     * CPU 집약적인 작업이므로 백그라운드 스레드에서 수행됩니다.
     */
    public void cropImage(Bitmap originalBitmap, Rect cropRect, OnImageCroppedListener listener) {
        backgroundExecutor.execute(() -> {
            int safeLeft = Math.max(cropRect.left, 0);
            int safeTop = Math.max(cropRect.top, 0);
            int safeWidth = Math.min(cropRect.width(), originalBitmap.getWidth() - safeLeft);
            int safeHeight = Math.min(cropRect.height(), originalBitmap.getHeight() - safeTop);

            Bitmap cropped = Bitmap.createBitmap(originalBitmap, safeLeft, safeTop, safeWidth, safeHeight);

            // 결과를 메인 스레드로 반환하려면 Activity에서 runOnUiThread 등을 사용해야 함을 설계상 유도
            listener.onCropped(cropped);
        });
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy planeProxy = image.getPlanes()[0];
        ByteBuffer buffer = planeProxy.getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        if (rotationDegrees == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public void shutDown() {
        backgroundExecutor.shutdown();
    }
}