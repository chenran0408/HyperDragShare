package com.leaf.hyperdragshare.codex;

import java.util.List;

/** AICR V2 text and raw line coordinates; no interpretation of the vendor geometry. */
public final class AicrOcrResult {
    public final String text;
    public final List<Line> lines;

    public AicrOcrResult(String text, List<Line> lines) {
        this.text = text;
        this.lines = List.copyOf(lines);
    }

    public static final class Line {
        public final String text;
        public final float[] polygon;
        public final float[] box;

        public Line(String text, float[] polygon, float[] box) {
            this.text = text;
            this.polygon = polygon;
            this.box = box;
        }
    }
}
