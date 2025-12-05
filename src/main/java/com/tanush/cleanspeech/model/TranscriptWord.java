package com.tanush.cleanspeech.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single transcribed word with its timestamps.
 * Used for processing transcription results from Gemini API.
 */
public class TranscriptWord {

    // The transcribed text of the word
    private String word;

    // Start time in seconds when this word begins
    @JsonProperty("start")
    private double startTimeSec;

    // End time in seconds when this word ends
    @JsonProperty("end")
    private double endTimeSec;

    // Default constructor for Jackson deserialization
    public TranscriptWord() {
    }

    // Constructor with all fields
    public TranscriptWord(String word, double startTimeSec, double endTimeSec) {
        this.word = word;
        this.startTimeSec = startTimeSec;
        this.endTimeSec = endTimeSec;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public double getStartTimeSec() {
        return startTimeSec;
    }

    public void setStartTimeSec(double startTimeSec) {
        this.startTimeSec = startTimeSec;
    }

    public double getEndTimeSec() {
        return endTimeSec;
    }

    public void setEndTimeSec(double endTimeSec) {
        this.endTimeSec = endTimeSec;
    }

    @Override
    public String toString() {
        return "TranscriptWord{" +
                "word='" + word + '\'' +
                ", start=" + startTimeSec +
                ", end=" + endTimeSec +
                '}';
    }
}
