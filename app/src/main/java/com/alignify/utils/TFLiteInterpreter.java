package com.alignify.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * TensorFlow Lite interpreter wrapper for exercise classification models.
 * Supports loading from both assets and cached files.
 */
public class TFLiteInterpreter {

    private final Interpreter interpreter;
    private final ByteBuffer inputBuffer;
    private final ByteBuffer outputBuffer;
    private final int[] inputShape;
    private final int[] outputShape;

    /**
     * Create interpreter from asset file.
     */
    public TFLiteInterpreter(Context context, String modelPath) throws IOException {
        MappedByteBuffer model = loadModelFromAsset(context, modelPath);
        interpreter = new Interpreter(model);

        // Initialize buffers
        inputShape = interpreter.getInputTensor(0).shape();
        outputShape = interpreter.getOutputTensor(0).shape();
        inputBuffer = allocateBuffer(inputShape);
        outputBuffer = allocateBuffer(outputShape);
    }

    /**
     * Create interpreter from file (for cached/downloaded models).
     */
    public TFLiteInterpreter(File modelFile) throws IOException {
        MappedByteBuffer model = loadModelFromFile(modelFile);
        interpreter = new Interpreter(model);

        // Initialize buffers
        inputShape = interpreter.getInputTensor(0).shape();
        outputShape = interpreter.getOutputTensor(0).shape();
        inputBuffer = allocateBuffer(inputShape);
        outputBuffer = allocateBuffer(outputShape);
    }

    private ByteBuffer allocateBuffer(int[] shape) {
        int size = 1;
        for (int dim : shape) {
            size *= dim;
        }
        size *= 4; // 4 bytes for float
        ByteBuffer buffer = ByteBuffer.allocateDirect(size);
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    private MappedByteBuffer loadModelFromAsset(Context context, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private MappedByteBuffer loadModelFromFile(File modelFile) throws IOException {
        FileInputStream inputStream = new FileInputStream(modelFile);
        FileChannel fileChannel = inputStream.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
    }

    /**
     * Run inference on input features.
     * 
     * @param input Float array of input features
     * @return Float array of output probabilities
     */
    public float[] predict(float[] input) {
        // Clear and populate input buffer
        inputBuffer.clear();
        for (float value : input) {
            inputBuffer.putFloat(value);
        }
        inputBuffer.rewind();

        // Clear output buffer
        outputBuffer.clear();

        // Run inference
        interpreter.run(inputBuffer, outputBuffer);

        // Extract output
        outputBuffer.rewind();
        int numClasses = outputShape[1];
        float[] output = new float[numClasses];
        for (int i = 0; i < numClasses; i++) {
            output[i] = outputBuffer.getFloat();
        }

        return output;
    }

    /**
     * Get the predicted class index.
     */
    public int predictClass(float[] input) {
        float[] probabilities = predict(input);
        int maxIndex = 0;
        float maxValue = probabilities[0];

        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > maxValue) {
                maxValue = probabilities[i];
                maxIndex = i;
            }
        }

        return maxIndex;
    }

    /**
     * Get expected input size.
     */
    public int getInputSize() {
        return inputShape[1];
    }

    /**
     * Get number of output classes.
     */
    public int getNumClasses() {
        return outputShape[1];
    }

    /**
     * Close the interpreter and release resources.
     */
    public void close() {
        interpreter.close();
    }
}
