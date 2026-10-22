package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public final class AicrBarcodeResultTest {
    @Test public void readsVendorQrAndLinearCodeRecordsInOrderWithoutDuplicatingValues() {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeInt(3);
            writeCode(parcel, "系统扫码", 256);
            writeCode(parcel, "HDS-211", 1);
            writeCode(parcel, "系统扫码", 256);
            parcel.setDataPosition(0);
            assertEquals(Arrays.asList("系统扫码", "HDS-211"), AicrBarcodeClient.readValues(parcel));
            assertEquals(0, parcel.dataAvail());
        } finally { parcel.recycle(); }
    }

    @Test public void emptyDetectionAndNullRecordsProduceNoCodes() {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeInt(0);
            parcel.setDataPosition(0);
            assertTrue(AicrBarcodeClient.readValues(parcel).isEmpty());
            parcel.setDataPosition(0);
            parcel.writeInt(1);
            parcel.writeInt(0);
            parcel.setDataPosition(0);
            assertTrue(AicrBarcodeClient.readValues(parcel).isEmpty());
        } finally { parcel.recycle(); }
    }

    private static void writeCode(Parcel parcel, String value, int format) {
        parcel.writeInt(1);
        parcel.writeString("7");
        parcel.writeString(value);
        parcel.writeInt(2);
        parcel.writeParcelable(new Point(10, 20), 0);
        parcel.writeParcelable(new Point(80, 100), 0);
        parcel.writeParcelable(new Rect(10, 20, 80, 100), 0);
        parcel.writeInt(format);
    }
}
