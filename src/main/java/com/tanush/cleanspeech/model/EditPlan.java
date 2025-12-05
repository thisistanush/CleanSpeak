package com.tanush.cleanspeech.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains the complete edit plan for an audio file.
 * Includes segments to remove completely and pauses to shorten.
 */
public class EditPlan {

    // List of segments to remove completely (filler words, etc.)
    private List<EditSegment> segmentsToRemove;

    // List of pauses to shorten (long silences between words)
    private List<EditSegment> pausesToShorten;

    // Default constructor initializes empty lists
    public EditPlan() {
        this.segmentsToRemove = new ArrayList<>();
        this.pausesToShorten = new ArrayList<>();
    }

    // Constructor with pre-populated lists
    public EditPlan(List<EditSegment> segmentsToRemove, List<EditSegment> pausesToShorten) {
        this.segmentsToRemove = segmentsToRemove != null ? segmentsToRemove : new ArrayList<>();
        this.pausesToShorten = pausesToShorten != null ? pausesToShorten : new ArrayList<>();
    }

    public List<EditSegment> getSegmentsToRemove() {
        return segmentsToRemove;
    }

    public void setSegmentsToRemove(List<EditSegment> segmentsToRemove) {
        this.segmentsToRemove = segmentsToRemove;
    }

    public List<EditSegment> getPausesToShorten() {
        return pausesToShorten;
    }

    public void setPausesToShorten(List<EditSegment> pausesToShorten) {
        this.pausesToShorten = pausesToShorten;
    }

    // Adds a segment to the remove list
    public void addSegmentToRemove(EditSegment segment) {
        this.segmentsToRemove.add(segment);
    }

    // Adds a pause to the shorten list
    public void addPauseToShorten(EditSegment segment) {
        this.pausesToShorten.add(segment);
    }

    // Returns total number of edits planned
    public int getTotalEditCount() {
        return segmentsToRemove.size() + pausesToShorten.size();
    }

    // Calculates total time that will be removed (in seconds)
    public double getTotalTimeToRemove() {
        double total = 0.0;
        for (EditSegment segment : segmentsToRemove) {
            total = total + segment.getDurationSec();
        }
        return total;
    }

    // Calculates total time saved from shortened pauses (in seconds)
    // Assumes pauses are shortened to 0.5 seconds each
    public double getTotalTimeSavedFromPauses() {
        double saved = 0.0;
        double targetPauseDuration = 0.5; // Shorten pauses to 0.5 seconds
        for (EditSegment pause : pausesToShorten) {
            double originalDuration = pause.getDurationSec();
            if (originalDuration > targetPauseDuration) {
                saved = saved + (originalDuration - targetPauseDuration);
            }
        }
        return saved;
    }

    @Override
    public String toString() {
        return "EditPlan{" +
                "segmentsToRemove=" + segmentsToRemove.size() +
                ", pausesToShorten=" + pausesToShorten.size() +
                ", totalTimeToRemove=" + String.format("%.2f", getTotalTimeToRemove()) + "s" +
                '}';
    }
}
