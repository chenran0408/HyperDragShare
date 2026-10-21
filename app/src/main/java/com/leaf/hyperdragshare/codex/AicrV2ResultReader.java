package com.leaf.hyperdragshare.codex;

import android.os.BadParcelableException;
import android.os.Parcel;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire reader for the V2 result verified with AICR 4.12.26.
 * Not an OCR engine: no service is bound and no vendor classes are loaded.
 */
final class AicrV2ResultReader {
    private static final String LOCATION = "com.xiaomi.aicr.vision.ocr.OCRRes$Location";

    private AicrV2ResultReader() {}

    /** Reads the typed result after the Binder exception header, including its presence marker. */
    static String readText(Parcel reply) {
        AicrOcrResult result = readResult(reply);
        return result == null ? null : result.text;
    }

    static AicrOcrResult readResult(Parcel reply) {
        if (!present(reply)) return null;
        List<AicrOcrResult.Line> lines = new ArrayList<>();
        array(reply, () -> {
            array(reply, () -> {
                Location coordinates = location(reply);
                String text = reply.readString();
                lines.add(new AicrOcrResult.Line(text,
                        coordinates == null ? null : coordinates.polygon,
                        coordinates == null ? null : coordinates.box));
                reply.readInt(); // line id
                array(reply, () -> {
                    location(reply);
                    reply.readString(); // character
                    reply.readFloat(); // confidence
                });
                reply.readFloat(); // line confidence
            });
            reply.readString(); // paragraph text
        });
        String text = reply.readString();
        array(reply, () -> {
            location(reply);
            reply.readString(); // entity text
            reply.readInt(); // type
            reply.readInt(); // line id
            reply.readInt(); // start
            reply.readInt(); // end
        });
        return new AicrOcrResult(text, lines);
    }

    private static boolean present(Parcel parcel) {
        int marker = parcel.readInt();
        if (marker != 0 && marker != 1) {
            throw new BadParcelableException("AICR V2: invalid presence marker " + marker);
        }
        return marker == 1;
    }

    private static void array(Parcel parcel, Runnable readItem) {
        int count = parcel.readInt();
        // Each typed item needs at least its presence int, even when null.
        if (count < -1 || count > parcel.dataAvail() / Integer.BYTES) {
            throw new BadParcelableException("AICR V2: invalid array length " + count);
        }
        for (int i = 0; i < count; i++) {
            if (present(parcel)) readItem.run();
        }
    }

    private static Location location(Parcel parcel) {
        String name = parcel.readString();
        if (name == null) return null;
        if (!LOCATION.equals(name)) {
            throw new BadParcelableException("AICR V2: unexpected location type " + name);
        }
        return new Location(parcel.createFloatArray(), parcel.createFloatArray());
    }

    private static final class Location {
        final float[] polygon;
        final float[] box;

        Location(float[] polygon, float[] box) {
            this.polygon = polygon;
            this.box = box;
        }
    }
}
