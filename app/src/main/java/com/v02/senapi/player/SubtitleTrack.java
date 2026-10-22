package com.v02.senapi.player;

import java.util.ArrayList;
import java.util.List;

public class SubtitleTrack {
    public static class Cue {
        public final long startTimeMs;
        public final long endTimeMs;
        public final String text;

        public Cue(long startTimeMs, long endTimeMs, String text) {
            this.startTimeMs = startTimeMs;
            this.endTimeMs = endTimeMs;
            this.text = text;
        }
    }

    private final String label;
    private final String url;
    private final List<Cue> cues;

    public SubtitleTrack(String label, String url) {
        this.label = label;
        this.url = url;
        this.cues = new ArrayList<>();
    }

    public String getLabel() {
        return label;
    }

    public String getUrl() {
        return url;
    }

    public List<Cue> getCues() {
        return cues;
    }

    public void setCues(List<Cue> newCues) {
        this.cues.clear();
        if (newCues != null) {
            this.cues.addAll(newCues);
        }
    }

    public String getActiveCueText(long positionMs) {
        for (Cue cue : cues) {
            if (positionMs >= cue.startTimeMs && positionMs <= cue.endTimeMs) {
                return cue.text;
            }
        }
        return null;
    }
}
