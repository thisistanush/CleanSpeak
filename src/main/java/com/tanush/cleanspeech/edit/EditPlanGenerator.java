package com.tanush.cleanspeech.edit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanush.cleanspeech.llm.GeminiClient;
import com.tanush.cleanspeech.model.EditPlan;
import com.tanush.cleanspeech.model.EditSegment;
import com.tanush.cleanspeech.model.TranscriptWord;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Generates an edit plan based on the transcript.
 * Uses FREE Gemini LLM for smart, context-aware filler word detection.
 * Falls back to rule-based detection if LLM is unavailable.
 */
public class EditPlanGenerator {

    // Common filler words for rule-based fallback
    private static final Set<String> FILLER_WORDS = Set.of(
            "um", "uh", "uhm", "umm", "uhh",
            "er", "err", "ah", "ahh",
            "hmm", "hm", "mhm");

    // Words that should only be removed at start of sentences
    private static final Set<String> SENTENCE_START_FILLERS = Set.of(
            "so", "well", "okay", "ok");

    // Minimum pause duration to consider for shortening (in seconds)
    private static final double MIN_PAUSE_FOR_SHORTENING = 1.5;

    // Jackson mapper for JSON parsing
    private final ObjectMapper objectMapper;

    // Gemini client (null if not available)
    private GeminiClient llmClient;

    // Whether to use LLM for edit plan generation
    private boolean useLlm;

    /**
     * Creates an EditPlanGenerator.
     * Automatically detects if Gemini API is available.
     */
    public EditPlanGenerator() {
        this.objectMapper = new ObjectMapper();
        this.useLlm = false;

        // Try to initialize Gemini client
        if (GeminiClient.isAvailable()) {
            try {
                this.llmClient = new GeminiClient();
                this.useLlm = true;
            } catch (Exception e) {
                // LLM unavailable, will use rule-based detection
            }
        }
    }

    /**
     * Generates an edit plan for the given transcript.
     *
     * @param words List of transcribed words with timestamps
     * @return EditPlan specifying segments to remove and pauses to shorten
     */
    public EditPlan generateEditPlan(List<TranscriptWord> words) {
        if (words == null || words.isEmpty()) {
            return new EditPlan();
        }

        // Try LLM-based generation first
        if (useLlm) {
            try {
                EditPlan llmPlan = generateWithLlm(words);
                if (llmPlan != null) {
                    return llmPlan;
                }
            } catch (Exception e) {
                // LLM failed, falling back to rules
            }
        }

        // Fallback to rule-based generation
        return generateWithRules(words);
    }

    /**
     * Generates an edit plan using Gemini LLM for fillers and Java logic for
     * pauses.
     */
    private EditPlan generateWithLlm(List<TranscriptWord> words) throws IOException {
        // 1. Get Fillers from LLM
        EditPlan plan = getFillersFromLlm(words);

        // 2. Add Pauses using strict Java logic (timestamps)
        addPausesFromTimestamps(words, plan);

        // 3. Post-process: Add padding, sort, and ensure no overlaps with content
        return postProcessPlan(plan, words);
    }

    /**
     * Calls LLM to identify fillers only.
     */
    private EditPlan getFillersFromLlm(List<TranscriptWord> words) throws IOException {
        // Build transcript JSON
        StringBuilder transcriptBuilder = new StringBuilder();
        transcriptBuilder.append("[\n");
        for (int i = 0; i < words.size(); i++) {
            TranscriptWord word = words.get(i);
            transcriptBuilder.append(String.format(
                    "  { \"word\": \"%s\", \"start\": %.2f, \"end\": %.2f }",
                    escapeJson(word.getWord()), word.getStartTimeSec(), word.getEndTimeSec()));
            if (i < words.size() - 1) {
                transcriptBuilder.append(",");
            }
            transcriptBuilder.append("\n");
        }
        transcriptBuilder.append("]");

        String systemPrompt = """
                You are an expert audio editor. Your ONLY job is to identify FILLER WORDS in a transcript.

                INPUT: A JSON array of words with timestamps.
                OUTPUT: A JSON object listing segments to remove.

                RULES FOR FILLER REMOVAL:
                1. Target these words: "um", "uh", "er", "ah", "hmm", "uhm", "umm".
                2. Target these phrases ONLY if they are non-meaningful fillers: "you know", "kind of", "sort of", "basically", "actually", "like".
                3. BE CONSERVATIVE:
                   - NEVER remove "like" if it's a verb ("I like it") or preposition ("like a boss").
                   - NEVER remove nouns, verbs, numbers, names, or technical terms.
                   - If unsure, KEEP IT.

                OUTPUT FORMAT:
                {
                  "remove_segments": [
                    { "start": 0.5, "end": 0.8, "reason": "FILLER: um" }
                  ]
                }

                Return ONLY valid JSON.
                """;

        String userPrompt = "Identify fillers in this transcript:\n" + transcriptBuilder.toString();

        String response = llmClient.sendChatRequest(systemPrompt, userPrompt);
        return parseLlmResponse(response);
    }

    /**
     * Adds pause shortening segments based on strict timestamp gaps.
     */
    private void addPausesFromTimestamps(List<TranscriptWord> words, EditPlan plan) {
        double PAUSE_THRESHOLD = 0.75; // If gap > 0.75s, shorten it
        double TARGET_PAUSE = 0.4; // Shorten to 0.4s

        // Check gaps between words
        for (int i = 0; i < words.size() - 1; i++) {
            TranscriptWord current = words.get(i);
            TranscriptWord next = words.get(i + 1);

            double gap = next.getStartTimeSec() - current.getEndTimeSec();

            if (gap > PAUSE_THRESHOLD) {
                // Add segment to shorten this pause
                // We want to replace the gap [end, start] with a silence of TARGET_PAUSE
                // The AudioEditor expects the segment to be the *original* range to be modified
                plan.addPauseToShorten(new EditSegment(
                        current.getEndTimeSec(),
                        next.getStartTimeSec(),
                        "LONG_PAUSE|" + TARGET_PAUSE));
            }
        }

        // Handle leading silence (before first word)
        if (!words.isEmpty()) {
            double firstStart = words.get(0).getStartTimeSec();
            if (firstStart > 0.5) {
                // Remove silence before first word, leaving 0.1s padding
                plan.addSegmentToRemove(new EditSegment(0.0, firstStart - 0.1, "LEADING_SILENCE"));
            }
        }
    }

    /**
     * Post-processes the plan: adds padding to fillers, merges overlaps, ensures
     * safety.
     */
    private EditPlan postProcessPlan(EditPlan rawPlan, List<TranscriptWord> words) {
        EditPlan finalPlan = new EditPlan();
        double PADDING = 0.05; // 50ms padding around fillers

        // 1. Process Removals (Fillers)
        for (EditSegment seg : rawPlan.getSegmentsToRemove()) {
            if (seg.getReason().contains("LEADING_SILENCE")) {
                finalPlan.addSegmentToRemove(seg);
                continue;
            }

            // Add padding
            double paddedStart = Math.max(0, seg.getStartTimeSec() - PADDING);
            double paddedEnd = seg.getEndTimeSec() + PADDING;

            // SAFETY CHECK: Ensure we don't overlap with ANY content words
            // We only want to remove the filler, not neighboring words.
            // Check against all words. If overlap, shrink the segment.

            for (TranscriptWord word : words) {
                // If this word is INSIDE the segment, it means the LLM marked a content word as
                // filler?
                // Or we padded into it.

                // Skip checking against the word itself if it IS the filler (approximate check)
                // But we don't know which word is the filler easily here without mapping back.
                // Conservative approach: If the original segment covers the word, assume it's
                // the filler.
                // If the PADDED segment touches a word that wasn't covered by original, shrink
                // padding.

                boolean originallyCovered = (word.getStartTimeSec() >= seg.getStartTimeSec()
                        && word.getEndTimeSec() <= seg.getEndTimeSec());

                if (!originallyCovered) {
                    // This is a neighbor. Don't touch it.
                    if (paddedStart < word.getEndTimeSec() && paddedEnd > word.getStartTimeSec()) {
                        // Overlap detected
                        if (word.getEndTimeSec() <= seg.getStartTimeSec()) {
                            // Word is before filler: clamp start
                            paddedStart = Math.max(paddedStart, word.getEndTimeSec());
                        }
                        if (word.getStartTimeSec() >= seg.getEndTimeSec()) {
                            // Word is after filler: clamp end
                            paddedEnd = Math.min(paddedEnd, word.getStartTimeSec());
                        }
                    }
                }
            }

            if (paddedEnd > paddedStart) {
                finalPlan.addSegmentToRemove(new EditSegment(paddedStart, paddedEnd, seg.getReason()));
            }
        }

        // 2. Process Pauses (pass through, they are generated from gaps so they are
        // safe)
        for (EditSegment pause : rawPlan.getPausesToShorten()) {
            finalPlan.addPauseToShorten(pause);
        }

        return finalPlan;
    }

    /**
     * Parses the LLM's JSON response into an EditPlan.
     */
    private EditPlan parseLlmResponse(String response) throws IOException {
        // Extract JSON from response (LLM might include explanation text)
        String jsonStr = extractJson(response);

        JsonNode root = objectMapper.readTree(jsonStr);
        EditPlan plan = new EditPlan();

        // Parse remove_segments
        JsonNode removeArray = root.get("remove_segments");
        if (removeArray != null && removeArray.isArray()) {
            for (JsonNode item : removeArray) {
                double start = item.get("start").asDouble();
                double end = item.get("end").asDouble();
                String reason = item.has("reason") ? item.get("reason").asText() : "FILLER_WORD";
                plan.addSegmentToRemove(new EditSegment(start, end, reason));
            }
        }

        // Ignore shorten_pauses from LLM as we do it in Java now

        return plan;
    }

    /**
     * Extracts JSON object from potentially mixed text response.
     */
    private String extractJson(String text) {
        // Find the first { and last } to extract JSON
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }

        return text;
    }

    /**
     * Generates an edit plan using simple word-matching rules.
     */
    private EditPlan generateWithRules(List<TranscriptWord> words) {
        EditPlan plan = new EditPlan();

        for (int i = 0; i < words.size(); i++) {
            TranscriptWord word = words.get(i);
            String normalized = word.getWord().toLowerCase().replaceAll("[^a-z ]", "").trim();

            boolean isFiller = false;

            // Check if it's a common filler word (strict list - no "like", "actually",
            // etc.)
            if (FILLER_WORDS.contains(normalized)) {
                isFiller = true;
            }

            // Check sentence-start fillers (only at beginning or after pause)
            if (!isFiller && SENTENCE_START_FILLERS.contains(normalized)) {
                boolean isAtStart = (i == 0);
                if (!isAtStart && i > 0) {
                    // Check if there was a pause before this word
                    TranscriptWord prevWord = words.get(i - 1);
                    double gap = word.getStartTimeSec() - prevWord.getEndTimeSec();
                    isAtStart = (gap > 0.5); // More than 0.5s gap suggests sentence start
                }
                if (isAtStart) {
                    isFiller = true;
                }
            }

            // Add filler to removal list
            if (isFiller) {
                plan.addSegmentToRemove(new EditSegment(
                        word.getStartTimeSec(),
                        word.getEndTimeSec(),
                        "FILLER_WORD: " + normalized));
            }

            // Check for long pauses between words
            if (i > 0) {
                TranscriptWord prevWord = words.get(i - 1);
                double pauseDuration = word.getStartTimeSec() - prevWord.getEndTimeSec();

                if (pauseDuration > MIN_PAUSE_FOR_SHORTENING) {
                    // Target new duration: keep 0.5s of the pause
                    double newDuration = 0.5;
                    plan.addPauseToShorten(new EditSegment(
                            prevWord.getEndTimeSec(),
                            word.getStartTimeSec(),
                            "LONG_PAUSE|" + newDuration));
                }
            }
        }

        return plan;
    }

    /**
     * Escapes special characters for JSON string.
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
