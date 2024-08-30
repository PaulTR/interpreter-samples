package com.google.edgeai.examples.model_personalization

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class ModelPersonalizationHelper(private val context: Context) {
    companion object {
        private const val TAG = "ModelPersonalizationHelper"
        const val CLASS_ONE = "1"
        const val CLASS_TWO = "2"
        const val CLASS_THREE = "3"
        const val CLASS_FOUR = "4"
        private val classes = mapOf(
            CLASS_ONE to 0,
            CLASS_TWO to 1,
            CLASS_THREE to 2,
            CLASS_FOUR to 3
        )
        private const val LOAD_BOTTLENECK_INPUT_KEY = "feature"
        private const val LOAD_BOTTLENECK_OUTPUT_KEY = "bottleneck"
        private const val LOAD_BOTTLENECK_KEY = "load"

        private const val TRAINING_INPUT_BOTTLENECK_KEY = "bottleneck"
        private const val TRAINING_INPUT_LABELS_KEY = "label"
        private const val TRAINING_OUTPUT_KEY = "loss"
        private const val TRAINING_KEY = "train"

        private const val INFERENCE_INPUT_KEY = "feature"
        private const val INFERENCE_OUTPUT_KEY = "output"
        private const val INFERENCE_KEY = "infer"

        private const val BOTTLENECK_SIZE = 1 * 7 * 7 * 1280
        private const val EXPECTED_BATCH_SIZE = 20
    }


    private val trainingSamples: MutableList<TrainingSample> = mutableListOf()

    private var interpreter: Interpreter? = null

    init {
        initClassifier()
    }


    /** Init a Interpreter from model_personalization.*/
    private fun initClassifier(delegate: Delegate = Delegate.CPU) {
        interpreter = try {
            val litertBuffer = FileUtil.loadMappedFile(context, "model.tflite")
            Log.i(TAG, "Done creating TFLite buffer from model_personalization")
            val options = Interpreter.Options().apply {
                numThreads = 4
                useNNAPI = delegate == Delegate.NNAPI
            }
            Interpreter(litertBuffer, options)
        } catch (e: Exception) {
            Log.i(TAG, "Create TFLite from model_personalization is failed ${e.message}")
            null
        }
    }

    // Process input image and add the output into list samples which are
    // ready for training.
    suspend fun addSample(imageProxy: ImageProxy, className: String) {
        if (interpreter == null) {
            return
        }
        withContext(Dispatchers.IO) {
            val tensorImage = processInputImage(imageProxy) ?: return@withContext
            val bottleneck = loadBottleneck(tensorImage)
            Log.d(TAG, "addSample: ++++")
            trainingSamples.add(
                TrainingSample(
                    bottleneck,
                    encoding(classes.getValue(CLASS_ONE))
                )
            )
        }
    }

    // encode the classes name to float array
    private fun encoding(id: Int): FloatArray {
        val classEncoded = FloatArray(4) { 0f }
        classEncoded[id] = 1f
        return classEncoded
    }


    // Loads the bottleneck feature from the given image array.
    private fun loadBottleneck(image: TensorImage): FloatArray {
        val inputs: MutableMap<String, Any> = HashMap()
        inputs[LOAD_BOTTLENECK_INPUT_KEY] = image.buffer
        val outputs: MutableMap<String, Any> = HashMap()
        val bottleneck = Array(1) { FloatArray(BOTTLENECK_SIZE) }
        outputs[LOAD_BOTTLENECK_OUTPUT_KEY] = bottleneck
        interpreter?.runSignature(inputs, outputs, LOAD_BOTTLENECK_KEY)
        return bottleneck[0]
    }

    suspend fun startTraining() {
        try {
            withContext(Dispatchers.IO) {
                if (interpreter == null) return@withContext
                val trainBatchSize = getTrainBatchSize()

                Log.d("xxx", "startTraining: start ${trainingSamples.size}");


                if (trainingSamples.size < trainBatchSize) {
                    Log.d(TAG, "startTraining: trainingSamples.size < trainBatchSize")
                    throw RuntimeException(
                        String.format(
                            "Too few samples to start training: need %d, got %d",
                            trainBatchSize, trainingSamples.size
                        )
                    )
                }

                val avgLoss: Float

                var totalLoss = 0f
                var numBatchesProcessed = 0

                // Shuffle training samples to reduce overfitting and
                // variance.
                trainingSamples.shuffle()

                trainingBatches(trainBatchSize)
                    .forEach { trainingSamples ->
                        val trainingBatchBottlenecks =
                            MutableList(trainBatchSize) {
                                FloatArray(
                                    BOTTLENECK_SIZE
                                )
                            }

                        val trainingBatchLabels =
                            MutableList(trainBatchSize) {
                                FloatArray(
                                    classes.size
                                )
                            }

                        // Copy a training sample list into two different
                        // input training lists.
                        trainingSamples.forEachIndexed { index, trainingSample ->
                            trainingBatchBottlenecks[index] =
                                trainingSample.bottleneck
                            trainingBatchLabels[index] =
                                trainingSample.label
                        }

                        val loss = training(
                            trainingBatchBottlenecks,
                            trainingBatchLabels
                        )
                        totalLoss += loss
                        numBatchesProcessed++
                    }

                // Calculate the average loss after training all batches.
                avgLoss = totalLoss / numBatchesProcessed
                Log.d(TAG, "startTraining: end");
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error occurred: ${e.message}")
        }
    }

    // Runs one training step with the given bottleneck batches and labels
    // and return the loss number.
    private fun training(
        bottlenecks: MutableList<FloatArray>,
        labels: MutableList<FloatArray>
    ): Float {
        val inputs: MutableMap<String, Any> = HashMap()
        inputs[TRAINING_INPUT_BOTTLENECK_KEY] = bottlenecks.toTypedArray()
        inputs[TRAINING_INPUT_LABELS_KEY] = labels.toTypedArray()

        val outputs: MutableMap<String, Any> = HashMap()
        val loss = FloatBuffer.allocate(1)
        outputs[TRAINING_OUTPUT_KEY] = loss

        interpreter?.runSignature(inputs, outputs, TRAINING_KEY)
        return loss.get(0)
    }


    private suspend fun processInputImage(imageProxy: ImageProxy): TensorImage? {
        return withContext(Dispatchers.IO) {
            val rotation = -imageProxy.imageInfo.rotationDegrees / 90
//            val (_, h, w, _) = interpreter?.getInputTensor(0)?.shape() ?: return@withContext null
            val height = imageProxy.height
            val width = imageProxy.width
            val imageProcessor =
                ImageProcessor
                    .Builder()
                    .add(ResizeWithCropOrPadOp(height, width))
                    .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                    .add(Rot90Op(rotation))
                    .add(NormalizeOp(0f, 255f))
                    .build()

            // Preprocess the image and convert it into a TensorImage for segmentation.
            imageProcessor.process(TensorImage.fromBitmap(imageProxy.toBitmap()))
        }
    }

    // Constructs an iterator that iterates over training sample batches.
    private fun trainingBatches(trainBatchSize: Int): Iterator<List<TrainingSample>> {
        return object : Iterator<List<TrainingSample>> {
            private var nextIndex = 0

            override fun hasNext(): Boolean {
                return nextIndex < trainingSamples.size
            }

            override fun next(): List<TrainingSample> {
                val fromIndex = nextIndex
                val toIndex: Int = nextIndex + trainBatchSize
                nextIndex = toIndex
                return if (toIndex >= trainingSamples.size) {
                    // To keep batch size consistent, last batch may include some elements from the
                    // next-to-last batch.
                    trainingSamples.subList(
                        trainingSamples.size - trainBatchSize,
                        trainingSamples.size
                    )
                } else {
                    trainingSamples.subList(fromIndex, toIndex)
                }
            }
        }
    }

    // Training model expected batch size.
    private fun getTrainBatchSize(): Int {
        return min(
            max( /* at least one sample needed */1, trainingSamples.size),
            EXPECTED_BATCH_SIZE
        )
    }

    enum class Delegate {
        CPU, NNAPI
    }

    data class TrainingSample(val bottleneck: FloatArray, val label: FloatArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TrainingSample

            if (!bottleneck.contentEquals(other.bottleneck)) return false
            if (!label.contentEquals(other.label)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = bottleneck.contentHashCode()
            result = 31 * result + label.contentHashCode()
            return result
        }
    }
}