package com.alignify.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * TensorFlow Lite interpreter wrapper for exercise classification models.
 */
public class TFLiteInterpreter {

    private final Interpreter interpreter;
    private final ByteBuffer inputBuffer;
    private final ByteBuffer outputBuffer;
    private final int[] inputShape;
    private final int[] outputShape;

    public TFLiteInterpreter(Context context, String modelPath) throws IOException {
        MappedByteBuffer model = loadModelFile(context, modelPath);
        interpreter = new Interpreter(model);

        // Get input and output tensor info
        inputShape = interpreter.getInputTensor(0).shape();
        outputShape = interpreter.getOutputTensor(0).shape();

        // Allocate input buffer
        int inputSize = 1;
        for (int dim : inputShape) {
            inputSize *= dim;
        }
        inputSize *= 4; // 4 bytes for float
        inputBuffer = ByteBuffer.allocateDirect(inputSize);
        inputBuffer.order(ByteOrder.nativeOrder());

        // Allocate output buffer
        int outputSize = 1;
        for (int dim : outputShape) {
            outputSize *= dim;
        }
        outputSize *= 4;
        outputBuffer = ByteBuffer.allocateDirect(outputSize);
        outputBuffer.order(ByteOrder.nativeOrder());
    }

    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
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
