package com.tanush.cleanspeech;

import com.tanush.cleanspeech.audio.AudioConverter;
import com.tanush.cleanspeech.audio.AudioEditor;
import com.tanush.cleanspeech.audio.NoiseReducer;
import com.tanush.cleanspeech.edit.EditPlanGenerator;
import com.tanush.cleanspeech.model.EditPlan;
import com.tanush.cleanspeech.model.TranscriptWord;
import com.tanush.cleanspeech.transcript.TranscriptWriter;
import com.tanush.cleanspeech.transcribe.GeminiTranscriber;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * CleanSpeech Audio Editor - Main Application
 *
 * Automatically removes filler words (um, uh, like, etc.) and shortens long
 * pauses
 * in audio recordings using FREE Gemini 2.0 Flash for transcription and
 * editing.
 *
 * This is a 100% Java solution using FREE APIs.
 *
 * Usage:
 * java -jar clean-speech-editor.jar <input.mp3> [output.mp3]
 *
 * Or run directly from IDE - will prompt for file path if no arguments.
 *
 * Requirements:
 * - ffmpeg (for audio conversion and noise reduction)
 * - GEMINI_API_KEY in config.properties (FREE from Google AI Studio)
 */
public class CleanSpeechApp {

    // Version string for display
    private static final String VERSION = "1.0.0";

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║         CleanSpeech Audio Editor v" + VERSION + "    ║");
        System.out.println("║   Remove filler words & clean your audio recordings  ║");
        System.out.println("║              Using FREE Gemini 2.0 Flash             ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();

        // Check for Gemini API key
        if (!GeminiTranscriber.isAvailable()) {
            System.err.println("Error: GEMINI_API_KEY not found in config.properties");
            System.err.println();
            System.err.println("To fix this:");
            System.err.println("  1. Open config.properties in the project root");
            System.err.println("  2. Add: GEMINI_API_KEY=your-key-here");
            System.err.println();
            System.err.println("Get a FREE key from: https://aistudio.google.com/apikey");
            System.exit(1);
        }

        // Get input and output paths
        Path inputMp3;
        Path outputMp3;

        if (args.length >= 1) {
            // Command line arguments provided
            inputMp3 = Paths.get(args[0]).toAbsolutePath();
            if (args.length >= 2) {
                outputMp3 = Paths.get(args[1]).toAbsolutePath();
            } else {
                String inputName = inputMp3.getFileName().toString();
                String baseName = inputName.substring(0, inputName.lastIndexOf('.'));
                outputMp3 = inputMp3.getParent().resolve(baseName + "_cleaned.mp3");
            }
        } else {
            // No arguments - try to find a sample file or show usage
            Path currentDir = Paths.get("").toAbsolutePath();

            // Look for any MP3 file in current directory
            Path sampleMp3 = findSampleMp3(currentDir);
            if (sampleMp3 != null) {
                inputMp3 = sampleMp3;
                String inputName = inputMp3.getFileName().toString();
                String baseName = inputName.substring(0, inputName.lastIndexOf('.'));
                outputMp3 = inputMp3.getParent().resolve(baseName + "_cleaned.mp3");
                System.out.println("No arguments provided. Found MP3 file: " + inputMp3.getFileName());
            } else {
                printUsage();
                System.exit(1);
                return;
            }
        }

        // Validate input file exists
        if (!Files.exists(inputMp3)) {
            System.err.println("Error: Input file not found: " + inputMp3);
            System.exit(1);
        }

        System.out.println("Input:  " + inputMp3);
        System.out.println("Output: " + outputMp3);
        System.out.println();

        // Run the pipeline
        try {
            runPipeline(inputMp3, outputMp3);
            System.out.println();
            System.out.println("✓ Processing complete!");
            System.out.println("  Output saved to: " + outputMp3);
        } catch (Exception e) {
            System.err.println();
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Finds a sample MP3 file in the given directory.
     */
    private static Path findSampleMp3(Path directory) {
        try {
            return Files.list(directory)
                    .filter(p -> p.toString().toLowerCase().endsWith(".mp3"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Runs the complete audio processing pipeline.
     */
    private static void runPipeline(Path inputMp3, Path outputMp3) throws IOException {
        long startTime = System.currentTimeMillis();

        // Create temp working directory
        Path tempDir = Files.createTempDirectory("cleanspeech_");
        System.out.println();

        // Initialize components
        AudioConverter converter = new AudioConverter();
        NoiseReducer noiseReducer = new NoiseReducer();
        GeminiTranscriber transcriber = new GeminiTranscriber();
        EditPlanGenerator editGenerator = new EditPlanGenerator();
        AudioEditor editor = new AudioEditor();

        // Step 1: Convert MP3 to WAV
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("Step 1/5: Converting MP3 to WAV");
        System.out.println("═══════════════════════════════════════════════════════");
        Path rawWav = converter.convertMp3ToWav(inputMp3, tempDir);
        System.out.println();

        // Step 2: Apply noise reduction
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("Step 2/5: Reducing background noise");
        System.out.println("═══════════════════════════════════════════════════════");
        Path denoisedWav = noiseReducer.reduceNoise(rawWav, tempDir);
        System.out.println();

        // Step 3: Transcribe with Gemini
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("Step 3/5: Transcribing audio with Gemini (FREE)");
        System.out.println("═══════════════════════════════════════════════════════");
        List<TranscriptWord> words = transcriber.transcribe(denoisedWav, tempDir);
        System.out.println();

        // Show a preview of the transcript
        if (!words.isEmpty()) {
            System.out.println("Transcript preview:");
            StringBuilder preview = new StringBuilder("  ");
            int wordCount = 0;
            for (TranscriptWord word : words) {
                preview.append(word.getWord()).append(" ");
                wordCount++;
                if (wordCount >= 20) {
                    preview.append("...");
                    break;
                }
            }
            System.out.println(preview.toString());
            System.out.println();
        }

        // Step 4: Generate edit plan
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("Step 4/5: Analyzing transcript for filler words");
        System.out.println("═══════════════════════════════════════════════════════");
        EditPlan editPlan = editGenerator.generateEditPlan(words);
        System.out.println();

        // Show edit summary
        System.out.println("Edit plan summary:");
        System.out.println("  Filler words to remove: " + editPlan.getSegmentsToRemove().size());
        System.out.println("  Long pauses to shorten: " + editPlan.getPausesToShorten().size());
        System.out.println("  Estimated time saved: " +
                String.format("%.1f", editPlan.getTotalTimeToRemove() + editPlan.getTotalTimeSavedFromPauses()) + "s");
        System.out.println();

        // Generate transcript files
        try {
            System.out.println("Generating transcripts...");
            TranscriptWriter transcriptWriter = new TranscriptWriter();
            transcriptWriter.writeTranscripts(words, editPlan, outputMp3);
        } catch (Exception e) {
            System.out.println("Warning: Could not generate transcripts: " + e.getMessage());
        }
        System.out.println();

        // Step 5: Apply edits and create output
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("Step 5/5: Editing audio and creating output");
        System.out.println("═══════════════════════════════════════════════════════");
        Path cleanedWav = editor.applyEditPlan(denoisedWav, editPlan, tempDir);
        System.out.println();

        // Convert cleaned WAV back to MP3
        System.out.println("Converting to MP3...");
        converter.convertWavToMp3(cleanedWav, outputMp3);
        System.out.println();

        // Print final statistics
        long endTime = System.currentTimeMillis();
        double processingTime = (endTime - startTime) / 1000.0;

        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("                    PROCESSING COMPLETE                 ");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("Statistics:");
        System.out.println("  Words transcribed:      " + words.size());
        System.out.println("  Filler words removed:   " + editPlan.getSegmentsToRemove().size());
        System.out.println("  Pauses shortened:       " + editPlan.getPausesToShorten().size());
        System.out.println("  Time saved:             " +
                String.format("%.1f", editPlan.getTotalTimeToRemove() + editPlan.getTotalTimeSavedFromPauses()) + "s");
        System.out.println("  Processing time:        " + String.format("%.1f", processingTime) + "s");

        // Cleanup temp files
        cleanupTempDir(tempDir);
    }

    /**
     * Cleans up the temporary directory.
     */
    private static void cleanupTempDir(Path tempDir) {
        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    /**
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("Usage: java -jar clean-speech-editor.jar <input.mp3> [output.mp3]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  input.mp3   - Path to the input MP3 file");
        System.out.println("  output.mp3  - (Optional) Path for the output file");
        System.out.println("                Default: inputname_cleaned.mp3");
        System.out.println();
        System.out.println("Requirements:");
        System.out.println("  - ffmpeg must be installed and in PATH");
        System.out.println("  - GEMINI_API_KEY in config.properties (FREE from Google AI Studio)");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar clean-speech-editor.jar recording.mp3");
        System.out.println("  java -jar clean-speech-editor.jar input.mp3 output.mp3");
        System.out.println();
        System.out.println("Tip: Place an MP3 file in the current directory and run without arguments.");
    }
}
