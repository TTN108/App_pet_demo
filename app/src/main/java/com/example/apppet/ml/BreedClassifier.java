package com.example.apppet.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Nhận diện giống thú cưng bằng model TensorFlow Lite do tools/train_breed_classifier.py tạo
 * (assets/ml/breed_classifier.tflite + assets/ml/labels.txt). Nhãn là breed_slug, được map về bảng breeds
 * qua breed_aliases (CatalogDatabase.findBreedByLabel). Không thread-safe: dùng trong một luồng nền.
 */
public final class BreedClassifier implements AutoCloseable {
    public static final String MODEL_NAME = "mobilenetv2_breed_v1";
    /** Dưới ngưỡng này app báo "chưa chắc chắn" và để người dùng tự chọn giống. */
    public static final float CONFIDENCE_THRESHOLD = 0.55f;
    private static final String MODEL_ASSET = "ml/breed_classifier.tflite";
    private static final String LABELS_ASSET = "ml/labels.txt";

    public static final class Prediction {
        public final String label;
        public final float probability;

        Prediction(String label, float probability) {
            this.label = label;
            this.probability = probability;
        }
    }

    private final Interpreter interpreter;
    private final List<String> labels;
    private final int inputWidth;
    private final int inputHeight;
    private final boolean quantizedInput;

    /** Có file model trong assets hay chưa (chưa chạy train_breed_classifier.py thì false). */
    public static boolean isModelPresent(Context context) {
        try (AssetFileDescriptor fd = context.getAssets().openFd(MODEL_ASSET)) {
            return fd.getLength() > 0;
        } catch (IOException ex) {
            return false;
        }
    }

    public BreedClassifier(Context context) throws IOException {
        labels = readLabels(context);
        if (labels.isEmpty()) throw new IOException("labels.txt trống");
        interpreter = new Interpreter(loadModel(context));
        int[] shape = interpreter.getInputTensor(0).shape();           // [1, H, W, 3]
        inputHeight = shape[1];
        inputWidth = shape[2];
        quantizedInput = interpreter.getInputTensor(0).dataType() == DataType.UINT8;
        int outputs = interpreter.getOutputTensor(0).shape()[1];
        if (outputs != labels.size()) {
            interpreter.close();
            throw new IOException("Số nhãn (" + labels.size() + ") không khớp đầu ra của model (" + outputs + ")");
        }
    }

    public List<Prediction> classify(Bitmap bitmap, int topK) {
        Bitmap input = prepare(bitmap);
        ByteBuffer buffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * 3 * (quantizedInput ? 1 : 4))
                .order(ByteOrder.nativeOrder());
        int[] pixels = new int[inputWidth * inputHeight];
        input.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight);
        for (int p : pixels) {
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            if (quantizedInput) {
                buffer.put((byte) r).put((byte) g).put((byte) b);
            } else {
                buffer.putFloat(r).putFloat(g).putFloat(b);             // 0..255, model đã gắn sẵn bước chuẩn hoá
            }
        }
        buffer.rewind();
        if (input != bitmap) input.recycle();

        float[][] output = new float[1][labels.size()];
        interpreter.run(buffer, output);

        List<Prediction> all = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) all.add(new Prediction(labels.get(i), output[0][i]));
        Collections.sort(all, (a, b) -> Float.compare(b.probability, a.probability));
        return new ArrayList<>(all.subList(0, Math.min(topK, all.size())));
    }

    /** Cắt vuông ở giữa rồi co về kích thước đầu vào của model. */
    private Bitmap prepare(Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        Bitmap square = Bitmap.createBitmap(src, (src.getWidth() - size) / 2, (src.getHeight() - size) / 2, size, size);
        Bitmap scaled = Bitmap.createScaledBitmap(square, inputWidth, inputHeight, true);
        if (square != src && square != scaled) square.recycle();
        return scaled;
    }

    private static MappedByteBuffer loadModel(Context context) throws IOException {
        try (AssetFileDescriptor fd = context.getAssets().openFd(MODEL_ASSET);
             FileInputStream in = new FileInputStream(fd.getFileDescriptor());
             FileChannel channel = in.getChannel()) {
            return channel.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        }
    }

    private static List<String> readLabels(Context context) throws IOException {
        List<String> out = new ArrayList<>();
        try (InputStream in = context.getAssets().open(LABELS_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) out.add(line.trim());
            }
        }
        return out;
    }

    @Override
    public void close() {
        interpreter.close();
    }
}
