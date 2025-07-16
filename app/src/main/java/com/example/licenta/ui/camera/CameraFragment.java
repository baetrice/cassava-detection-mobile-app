package com.example.licenta.ui.camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;

import com.example.licenta.R;
import com.example.licenta.utils.TFLiteModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraFragment extends Fragment {
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private PreviewView viewFinder;
    private TextView resultTextView;
    private TextView accuracyTextView;
    private TFLiteModel tfliteModel;
    private ExecutorService cameraExecutor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_camera, container, false);
        viewFinder = root.findViewById(R.id.viewFinder); //
        resultTextView = root.findViewById(R.id.result_text_view);
        accuracyTextView = root.findViewById(R.id.accuracy_text_view);
        tfliteModel = new TFLiteModel(requireActivity());
        cameraExecutor = Executors.newSingleThreadExecutor();
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }

        return root;
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(requireContext(), "Camera permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCamera() {

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // Handle any errors (including cancellation) here.
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }


    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {

        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(224, 224))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
            Bitmap bitmap = imageProxyToBitmap(image);
            if (bitmap != null) {
                runPrediction(bitmap);
            }
            image.close();
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis, preview);

        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap != null) {
            bitmap = rotateBitmap(bitmap, image.getImageInfo().getRotationDegrees());
        }
        return bitmap;
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void runPrediction(Bitmap bitmap) {
        float[] input = preprocessImage(bitmap);
        TFLiteModel.PredictionResult result = tfliteModel.predict(input);

        getActivity().runOnUiThread(() -> {
            displayResult(result.label, result.accuracy, result.latency);
        });
    }

    private float[] preprocessImage(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, TFLiteModel.IMG_SIZE, TFLiteModel.IMG_SIZE, true);

        int width = resizedBitmap.getWidth();
        int height = resizedBitmap.getHeight();
        int[] pixels = new int[width * height];
        resizedBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        float[] input = new float[width * height * 3];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            input[i * 3] = ((pixel >> 16) & 0xFF) / 255.0f; // Red
            input[i * 3 + 1] = ((pixel >> 8) & 0xFF) / 255.0f; // Green
            input[i * 3 + 2] = (pixel & 0xFF) / 255.0f; // Blue
        }

        return input;
    }

    private void displayResult(String label, float accuracy, long latency) {
        resultTextView.setText("Prediction: " + label);
        resultTextView.setVisibility(View.VISIBLE);

        accuracyTextView.setText(String.format("Accuracy: %.2f%%", accuracy));
        accuracyTextView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tfliteModel.close();
        cameraExecutor.shutdown();
    }
}
