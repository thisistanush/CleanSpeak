# CleanSpeak
Turn raw voice recordings into smooth, confident speech. CleanSpeak removes filler words, trims awkward silence, reduces noise, and boosts your voice automatically.

# How To Run
mvn clean package -DskipTests
java -jar target/clean-speech-editor-1.0-SNAPSHOT-jar-with-dependencies.jar
java -cp target/clean-speech-editor-1.0-SNAPSHOT-jar-with-dependencies.jar \
     com.tanush.cleanspeech.CleanSpeechApp input.mp3
