#!/bin/bash
# Run CleanSpeech UI with local JavaFX SDK

JAVAFX_PATH="/Users/tanushmusham/Documents/java_stuff/javafx-sdk-25.0.1/lib"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIB_DIR="$PROJECT_DIR/target/lib"

# Check if lib directory exists and has JARs
if [ ! -d "$LIB_DIR" ] || [ -z "$(ls -A "$LIB_DIR")" ]; then
    echo "Dependencies not found. Running Maven build..."
    export JAVA_HOME="/Users/tanushmusham/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home"
    mvn clean compile
fi

# Build classpath with all JARs
CLASSPATH="$PROJECT_DIR/target/classes"
for jar in "$LIB_DIR"/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done

echo "Starting CleanSpeech UI..."
java --module-path "$JAVAFX_PATH" \
     --add-modules javafx.controls,javafx.fxml,javafx.graphics \
     --enable-native-access=javafx.graphics \
     -cp "$CLASSPATH" \
     com.tanush.cleanspeech.ui.CleanSpeechUI
