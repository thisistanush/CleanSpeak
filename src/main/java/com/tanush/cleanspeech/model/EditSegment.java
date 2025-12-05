package com.tanush.cleanspeech.model;

/**
 * Represents a segment of audio to be edited (removed or shortened).
 * Contains the time range and the reason for the edit.
 */
public class EditSegment {

    // Start time in seconds of the segment to edit
    private double startTimeSec;

    // End time in seconds of the segment to edit
    private double endTimeSec;

    // Reason for this edit (e.g., "FILLER_WORD", "LONG_PAUSE")
    private String reason;

    // Default constructor
    public EditSegment() {
    }

    // Constructor with all fields
    public EditSegment(double startTimeSec, double endTimeSec, String reason) {
        this.startTimeSec = startTimeSec;
        this.endTimeSec = endTimeSec;
        this.reason = reason;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    // Returns the duration of this segment in seconds
    public double getDurationSec() {
        return endTimeSec - startTimeSec;
    }

    @Override
    public String toString() {
        return "EditSegment{" +
                "start=" + startTimeSec +
                ", end=" + endTimeSec +
                ", reason='" + reason + '\'' +
                '}';
    }
}
