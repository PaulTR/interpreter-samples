
# Download mnist_metadata.tflite from the internet if it's not exist.
TFLITE_FILE=./DigitClassifier/Helper/mnist_metadata.tflite
if test -f "$TFLITE_FILE"; then
    echo "INFO: mnist_metadata.tflite existed. Skip downloading and use the local model."
else
    curl -o ${TFLITE_FILE} -L https://storage.googleapis.com/ai-edge/interpreter-samples/digit_classifier/ios/mnist_metadata.tflite
    echo "INFO: Downloaded mnist_metadata.tflite to $TFLITE_FILE ."
fi
