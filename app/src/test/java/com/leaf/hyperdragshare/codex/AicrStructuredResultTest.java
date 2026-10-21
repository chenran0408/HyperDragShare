package com.leaf.hyperdragshare.codex;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public final class AicrStructuredResultTest {
    private static final String LOCATION = "com.xiaomi.aicr.vision.ocr.OCRRes$Location";
    private static final String TEXT = "上行\n无坐标行\n下行";
    private static final float[] POLYGON = {12f, 30f, 142f, 29f, 143f, 61f, 13f, 62f};
    private static final float[] BOX = {11f, 28f, 133f, 35f};

    @Test public void preservesRawLineOrderAndConsumesCharacterAndEntityPayloads() {
        Parcel reply = sample();
        try {
            AicrOcrResult result = AicrV2ResultReader.readResult(reply);
            assertEquals(TEXT, result.text);
            assertEquals(3, result.lines.size());
            assertEquals("上行", result.lines.get(0).text);
            assertArrayEquals(POLYGON, result.lines.get(0).polygon, 0f);
            assertArrayEquals(BOX, result.lines.get(0).box, 0f);
            assertEquals("无坐标行", result.lines.get(1).text);
            assertNull(result.lines.get(1).polygon);
            assertNull(result.lines.get(1).box);
            assertEquals("下行", result.lines.get(2).text);
            assertArrayEquals(new float[]{200f, 300f, 230f, 320f}, result.lines.get(2).box, 0f);
            assertEquals(reply.dataSize(), reply.dataPosition());
        } finally { reply.recycle(); }
    }

    private static Parcel sample() {
        Parcel reply = Parcel.obtain();
        reply.writeInt(1); // result
        reply.writeInt(2); // paragraphs
        reply.writeInt(1); // first paragraph
        reply.writeInt(2); // lines
        line(reply, "上行", 41, POLYGON, BOX, false);
        line(reply, "无坐标行", 42, null, null, true);
        reply.writeString("上行\n无坐标行");
        reply.writeInt(1); // second paragraph
        reply.writeInt(1); // lines
        line(reply, "下行", 73, new float[]{200f, 300f, 230f, 320f},
                new float[]{200f, 300f, 230f, 320f}, false);
        reply.writeString("下行");
        reply.writeString(TEXT);
        reply.writeInt(1); // entities
        reply.writeInt(1); // entity
        location(reply, new float[]{1f, 2f}, new float[]{3f, 4f});
        reply.writeString("entity");
        reply.writeInt(5); // type
        reply.writeInt(41); // line id
        reply.writeInt(0); // start
        reply.writeInt(2); // end
        reply.setDataPosition(0);
        return reply;
    }

    private static void line(Parcel reply, String text, int id, float[] polygon, float[] box,
                             boolean missingLocation) {
        reply.writeInt(1); // line
        if (missingLocation) reply.writeString(null); else location(reply, polygon, box);
        reply.writeString(text);
        reply.writeInt(id);
        reply.writeInt(2); // character entries include one absent typed item
        reply.writeInt(0);
        reply.writeInt(1);
        location(reply, new float[]{9f, 10f}, new float[]{8f, 11f});
        reply.writeString("字");
        reply.writeFloat(0.8f);
        reply.writeFloat(0.9f); // line confidence
    }

    private static void location(Parcel reply, float[] polygon, float[] box) {
        reply.writeString(LOCATION);
        reply.writeFloatArray(polygon);
        reply.writeFloatArray(box);
    }
}
