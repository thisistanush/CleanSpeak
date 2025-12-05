package com.tanush.cleanspeech.transcript;

import com.tanush.cleanspeech.model.EditPlan;
import com.tanush.cleanspeech.model.EditSegment;
import com.tanush.cleanspeech.model.TranscriptWord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates transcript text files from transcribed words.
 * Creates both raw transcripts and cleaned transcripts (with fillers removed).
 */
public class TranscriptWriter {

    // Target words per line for readable output
    private static final int WORDS_PER_LINE = 10;

    /**
     * Writes both raw and clean transcript files to the output directory.
     * Filenames are based on the output audio filename.
     *
     * @param words      List of transcribed words with timestamps
     * @param editPlan   The edit plan containing segments to remove
     * @param outputPath Path to the output MP3 file (used to derive transcript
     *                   names)
     */
    public void writeTranscripts(List<TranscriptWord> words, EditPlan editPlan, Path outputPath) {
        if (words == null || words.isEmpty()) {
            return;
        }

        // Derive transcript filenames from output MP3 path
        Path outputDir = outputPath.getParent();
        String baseName = getBaseName(outputPath.getFileName().toString());

        Path rawTranscriptPath = outputDir.resolve(baseName + "_raw_transcript.txt");
        Path cleanTranscriptPath = outputDir.resolve(baseName + "_clean_transcript.txt");

        try {
            writeRawTranscript(words, rawTranscriptPath);
        } catch (IOException e) {
            // Silently skip if transcript cannot be written
        }

        try {
            writeCleanTranscript(words, editPlan, cleanTranscriptPath);
        } catch (IOException e) {
            // Silently skip if transcript cannot be written
        }
    }

    /**
     * Writes the raw transcript with all words and timestamps.
     * Words are merged into readable lines.
     */
    private void writeRawTranscript(List<TranscriptWord> words, Path outputPath) throws IOException {
        StringBuilder transcript = new StringBuilder();
        List<TranscriptWord> currentLine = new ArrayList<>();

        for (TranscriptWord word : words) {
            currentLine.add(word);

            // Write line when we hit target length or encounter natural break
            if (currentLine.size() >= WORDS_PER_LINE || endsWithPunctuation(word.getWord())) {
                transcript.append(formatLine(currentLine));
                transcript.append("\n");
                currentLine.clear();
            }
        }

        // Write any remaining words
        if (!currentLine.isEmpty()) {
            transcript.append(formatLine(currentLine));
            transcript.append("\n");
        }

        Files.writeString(outputPath, transcript.toString());
    }

    /**
     * Writes the clean transcript with filler words removed.
     * Uses the edit plan to determine which words to skip.
     */
    private void writeCleanTranscript(List<TranscriptWord> words, EditPlan editPlan,
            Path outputPath) throws IOException {
        // Build set of time ranges to exclude (filler words only, not pauses)
        Set<Integer> wordsToRemove = findWordsToRemove(words, editPlan);

        StringBuilder transcript = new StringBuilder();
        List<TranscriptWord> currentLine = new ArrayList<>();

        for (int i = 0; i < words.size(); i++) {
            // Skip words that are being removed from audio
            if (wordsToRemove.contains(i)) {
                continue;
            }

            TranscriptWord word = words.get(i);
            currentLine.add(word);

            // Write line when we hit target length or encounter natural break
            if (currentLine.size() >= WORDS_PER_LINE || endsWithPunctuation(word.getWord())) {
                transcript.append(formatLine(currentLine));
                transcript.append("\n");
                currentLine.clear();
            }
        }

        // Write any remaining words
        if (!currentLine.isEmpty()) {
            transcript.append(formatLine(currentLine));
            transcript.append("\n");
        }

        Files.writeString(outputPath, transcript.toString());
    }

    /**
     * Identifies which words in the transcript should be removed based on the edit
     * plan.
     * Returns indices of words whose time ranges overlap with removal segments.
     */
    private Set<Integer> findWordsToRemove(List<TranscriptWord> words, EditPlan editPlan) {
        Set<Integer> toRemove = new HashSet<>();

        if (editPlan == null) {
            return toRemove;
        }

        for (EditSegment segment : editPlan.getSegmentsToRemove()) {
            // Skip pause removals (leading silence) - only remove actual filler words
            if (segment.getReason() != null && segment.getReason().contains("SILENCE")) {
                continue;
            }

            double segStart = segment.getStartTimeSec();
            double segEnd = segment.getEndTimeSec();

            for (int i = 0; i < words.size(); i++) {
                TranscriptWord word = words.get(i);
                double wordStart = word.getStartTimeSec();
                double wordEnd = word.getEndTimeSec();

                // Check if word overlaps with removal segment (with small tolerance)
                boolean overlaps = (wordStart < segEnd + 0.05) && (wordEnd > segStart - 0.05);
                if (overlaps) {
                    toRemove.add(i);
                }
            }
        }

        return toRemove;
    }

    /**
     * Formats a line of words with start and end timestamps.
     * Format: "00:00.50 - 00:01.21 Today we are going to talk about sorting
     * algorithms."
     */
    private String formatLine(List<TranscriptWord> words) {
        if (words.isEmpty()) {
            return "";
        }

        double startTime = words.get(0).getStartTimeSec();
        double endTime = words.get(words.size() - 1).getEndTimeSec();

        StringBuilder line = new StringBuilder();
        line.append(formatTimestamp(startTime));
        line.append(" - ");
        line.append(formatTimestamp(endTime));
        line.append(" ");

        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i).getWord();

            // Add space before word unless it's punctuation
            if (i > 0 && !startsWithPunctuation(word)) {
                line.append(" ");
            }

            line.append(word);
        }

        return line.toString();
    }

    /**
     * Formats a time in seconds to MM:SS.mm format.
     */
    private String formatTimestamp(double seconds) {
        int mins = (int) (seconds / 60);
        double secs = seconds % 60;
        return String.format("%02d:%05.2f", mins, secs);
    }

    /**
     * Checks if a word ends with sentence-ending punctuation.
     */
    private boolean endsWithPunctuation(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        char last = word.charAt(word.length() - 1);
        return last == '.' || last == '!' || last == '?';
    }

    /**
     * Checks if a word starts with punctuation (like comma or period).
     */
    private boolean startsWithPunctuation(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        char first = word.charAt(0);
        return first == ',' || first == '.' || first == '!' || first == '?'
                || first == ';' || first == ':';
    }

    /**
     * Extracts the base name from a filename (removes extension).
     */
    private String getBaseName(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            return filename.substring(0, dotIndex);
        }
        return filename;
    }
}
