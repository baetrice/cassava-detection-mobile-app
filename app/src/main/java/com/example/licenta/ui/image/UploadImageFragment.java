package com.example.licenta.ui.image;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.licenta.R;
import com.example.licenta.utils.TFLiteModel;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UploadImageFragment extends Fragment {
    private static final int REQUEST_IMAGE_SELECT = 1;
    private static final int IMG_SIZE = 224; // Image size used in training
    private ImageView imageView;
    private Button uploadButton;
    private Button predictButton;
    private TextView resultTextView;
    private TextView latencyTextView;
    private TextView accuracyTextView;
    private ProgressBar loadingIcon;
    private TFLiteModel tfliteModel;
    private Bitmap selectedBitmap;
    private ExecutorService executorService;
    private Handler mainHandler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_upload_image, container, false);
        imageView = root.findViewById(R.id.imageView);
        uploadButton = root.findViewById(R.id.button_upload_image);
        predictButton = root.findViewById(R.id.button_predict);
        resultTextView = root.findViewById(R.id.result_text_view);
        latencyTextView = root.findViewById(R.id.latency_text_view);
        accuracyTextView = root.findViewById(R.id.accuracy_text_view);
        loadingIcon = root.findViewById(R.id.loading_icon);
        tfliteModel = new TFLiteModel(requireActivity());

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_IMAGE_SELECT);
            }
        });

        predictButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedBitmap != null) {
                    runPrediction();
                } else {
                    Toast.makeText(getActivity(), "Please upload an image first", Toast.LENGTH_SHORT).show();
                }
            }
        });

        return root;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_SELECT && resultCode == Activity.RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                selectedBitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), imageUri);
                imageView.setImageBitmap(selectedBitmap);
                imageView.setVisibility(View.VISIBLE);
                predictButton.setVisibility(View.VISIBLE);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("UploadImageFragment", "Error processing the image", e);
                Toast.makeText(getActivity(), "Error processing the image", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e("UploadImageFragment", "Image selection failed or no data");
        }
    }

    private float[] preprocessImage(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMG_SIZE, IMG_SIZE, true);

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

        latencyTextView.setText("Latency: " + latency + " ms");
        latencyTextView.setVisibility(View.VISIBLE);
    }

    private void runPrediction() {
        loadingIcon.setVisibility(View.VISIBLE);
        resultTextView.setVisibility(View.GONE);
        latencyTextView.setVisibility(View.GONE);
        accuracyTextView.setVisibility(View.GONE);

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                float[] input = preprocessImage(selectedBitmap);
                final TFLiteModel.PredictionResult result = tfliteModel.predict(input);

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loadingIcon.setVisibility(View.GONE);
                        displayResult(result.label, result.accuracy, result.latency);
                    }
                });
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tfliteModel.close();
        executorService.shutdown();
    }
}
