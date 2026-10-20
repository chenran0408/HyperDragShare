package com.leaf.hyperdragshare.codex;

import android.os.BadParcelableException;
import android.os.Parcel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public final class AicrV2ResultReaderTest {
    @Test public void consumesNestedResultBeforeReadingTrailingData() {
        Parcel p = Parcel.obtain();
        try {
            p.writeInt(1); // result
            p.writeInt(1); p.writeInt(1); // paragraph array
            p.writeInt(1); p.writeInt(1); // line array
            p.writeString("com.xiaomi.aicr.vision.ocr.OCRRes$Location");
            p.writeFloatArray(new float[]{1, 2, 3, 4});
            p.writeFloatArray(new float[]{1, 2, 3, 4});
            p.writeString("你好"); p.writeInt(7);
            p.writeInt(1); p.writeInt(1); // character array
            p.writeString(null); p.writeString("你"); p.writeFloat(0.9f);
            p.writeFloat(0.8f);
            p.writeString("你好");
            p.writeString("你好\n第二段");
            p.writeInt(1); p.writeInt(1); // entity array
            p.writeString(null); p.writeString("你好");
            p.writeInt(2); p.writeInt(7); p.writeInt(0); p.writeInt(2);
            p.writeInt(12345);
            p.setDataPosition(0);
            assertEquals("你好\n第二段", AicrV2ResultReader.readText(p));
            assertEquals(12345, p.readInt());
            assertEquals(0, p.dataAvail());
        } finally { p.recycle(); }
    }

    @Test public void absentResultIsNotEmptyText() {
        Parcel p = Parcel.obtain();
        try {
            p.writeInt(0); p.setDataPosition(0);
            assertNull(AicrV2ResultReader.readText(p));
        } finally { p.recycle(); }
    }

    @Test public void rejectsImpossibleArrayLength() {
        Parcel p = Parcel.obtain();
        try {
            p.writeInt(1); p.writeInt(Integer.MAX_VALUE); p.setDataPosition(0);
            assertThrows(BadParcelableException.class, () -> AicrV2ResultReader.readText(p));
        } finally { p.recycle(); }
    }

    @Test public void rejectsUnknownParcelableInsteadOfLoadingItsClass() {
        Parcel p = Parcel.obtain();
        try {
            p.writeInt(1);
            p.writeInt(1); p.writeInt(1);
            p.writeInt(1); p.writeInt(1);
            p.writeString("unexpected.Type"); p.setDataPosition(0);
            assertThrows(BadParcelableException.class, () -> AicrV2ResultReader.readText(p));
        } finally { p.recycle(); }
    }
}
