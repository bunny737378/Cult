package com.v02.senapi.player;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VttParser {

    private static final Pattern TIMECODE_PATTERN = Pattern.compile(
            "^(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})[.,](\\d{3})\\s*-->\\s*(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})[.,](\\d{3})(?:\\s+.*)?$"
    );

    public static List<SubtitleTrack.Cue> parse(String content) {
        List<SubtitleTrack.Cue> cues = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return cues;
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            long startTime = -1;
            long endTime = -1;
            StringBuilder textBuilder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty()) {
                    if (startTime >= 0 && endTime >= 0 && textBuilder.length() > 0) {
                        String cleanText = cleanCueText(textBuilder.toString().trim());
                        cues.add(new SubtitleTrack.Cue(startTime, endTime, cleanText));
                    }
                    startTime = -1;
                    endTime = -1;
                    textBuilder.setLength(0);
                    continue;
                }

                if (line.startsWith("WEBVTT") || line.startsWith("NOTE") || line.startsWith("STYLE")) {
                    continue;
                }

                Matcher matcher = TIMECODE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    startTime = parseTime(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
                    endTime = parseTime(matcher.group(5), matcher.group(6), matcher.group(7), matcher.group(8));
                    continue;
                }

                // If not numeric sequence identifier line
                if (!line.matches("^\\d+$")) {
                    if (textBuilder.length() > 0) {
                        textBuilder.append("\n");
                    }
                    textBuilder.append(line);
                }
            }

            if (startTime >= 0 && endTime >= 0 && textBuilder.length() > 0) {
                String cleanText = cleanCueText(textBuilder.toString().trim());
                cues.add(new SubtitleTrack.Cue(startTime, endTime, cleanText));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return cues;
    }

    private static long parseTime(String hours, String minutes, String seconds, String millis) {
        long h = hours != null ? Long.parseLong(hours) : 0;
        long m = Long.parseLong(minutes);
        long s = Long.parseLong(seconds);
        long ms = Long.parseLong(millis);
        return (h * 3600000L) + (m * 60000L) + (s * 1000L) + ms;
    }

    private static String cleanCueText(String text) {
        // Strip HTML/VTT formatting tags like <c>, <b>, <i>, <v Name>
        return text.replaceAll("<[^>]*>", "").replaceAll("&lrm;|&rlm;", "");
    }
}
