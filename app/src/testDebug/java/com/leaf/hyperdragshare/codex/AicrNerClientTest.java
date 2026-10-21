package com.leaf.hyperdragshare.codex;

import android.os.BadParcelableException;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public final class AicrNerClientTest {
    @Test public void readsTypedEntitiesAndConsumesOnlyTheirWirePayload() {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeInt(2);
            parcel.writeInt(1);
            parcel.writeString("北京");
            parcel.writeInt(AicrNerClient.Entity.TYPE_LOCATION);
            parcel.writeInt(0);
            parcel.writeInt(2);
            parcel.writeInt(1);
            parcel.writeString("13800138000");
            parcel.writeInt(AicrNerClient.Entity.TYPE_TEL);
            parcel.writeInt(3);
            parcel.writeInt(14);
            parcel.writeInt(0x12345678);
            parcel.setDataPosition(0);

            List<AicrNerClient.Entity> entities = AicrNerClient.readEntities(parcel);

            assertEquals(2, entities.size());
            assertEquals("北京", entities.get(0).text);
            assertEquals(AicrNerClient.Entity.TYPE_LOCATION, entities.get(0).type);
            assertEquals(0, entities.get(0).start);
            assertEquals(2, entities.get(0).end);
            assertEquals("13800138000", entities.get(1).text);
            assertEquals(3, entities.get(1).start);
            assertEquals(14, entities.get(1).end);
            assertEquals(0x12345678, parcel.readInt());
            assertEquals(0, parcel.dataAvail());
        } finally {
            parcel.recycle();
        }
    }

    @Test public void rejectsImpossibleEntityCountBeforeAllocating() {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeInt(Integer.MAX_VALUE);
            parcel.setDataPosition(0);
            assertThrows(BadParcelableException.class, () -> AicrNerClient.readEntities(parcel));
        } finally {
            parcel.recycle();
        }
    }
}
