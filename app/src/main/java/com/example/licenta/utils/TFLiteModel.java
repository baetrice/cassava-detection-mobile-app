package com.example.licenta.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TFLiteModel {
    public static final int IMG_SIZE = 224;
    private Interpreter interpreter;
    private Map<Integer, String> labels;

    public TFLiteModel(Context context) {
        try {
            // Load model from assets
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd("model.tflite");
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            interpreter = new Interpreter(buffer);

            // Load labels from assets
            loadLabels(context);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadLabels(Context context) {
        labels = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("labels.json")));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            JSONObject jsonObject = new JSONObject(jsonBuilder.toString());
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                labels.put(Integer.parseInt(key), jsonObject.getString(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("TFLiteModel", "Error loading labels", e);
        }
    }

    public PredictionResult predict(float[] input) {
        // Ensure input is reshaped to [1, IMG_SIZE, IMG_SIZE, 3]
        float[][][][] inputTensor = new float[1][IMG_SIZE][IMG_SIZE][3];
        for (int i = 0; i < IMG_SIZE; i++) {
            for (int j = 0; j < IMG_SIZE; j++) {
                inputTensor[0][i][j][0] = input[(i * IMG_SIZE + j) * 3];
                inputTensor[0][i][j][1] = input[(i * IMG_SIZE + j) * 3 + 1];
                inputTensor[0][i][j][2] = input[(i * IMG_SIZE + j) * 3 + 2];
            }
        }

        // Adjust output shape according to model's output shape [1, 5]
        float[][] output = new float[1][5];
        long startTime = System.currentTimeMillis();
        interpreter.run(inputTensor, output);
        long endTime = System.currentTimeMillis();

        // Find the index with the highest probability
        int maxIndex = 0;
        for (int i = 1; i < output[0].length; i++) {
            if (output[0][i] > output[0][maxIndex]) {
                maxIndex = i;
            }
        }

        // Calculate latency
        long latency = endTime - startTime;

        // Map the index to the corresponding label and get the accuracy
        String label = labels.get(maxIndex);
        float accuracy = output[0][maxIndex] * 100; // Convert to percentage

        return new PredictionResult(label, accuracy, latency);
    }

    public void close() {
        interpreter.close();
    }

    public static class PredictionResult {
        public final String label;
        public final float accuracy;
        public final long latency;

        public PredictionResult(String label, float accuracy, long latency) {
            this.label = label;
            this.accuracy = accuracy;
            this.latency = latency;
        }
    }
}
