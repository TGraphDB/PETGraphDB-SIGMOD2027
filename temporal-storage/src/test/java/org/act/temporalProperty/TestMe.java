package org.act.temporalProperty;

import org.act.temporalProperty.impl.ValueType;
import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.Slices;
import org.act.temporalProperty.vo.EntityPropertyId;
import org.junit.Test;

import java.io.File;

public class TestMe {

    @Test
    public void functionTest() throws Throwable {
        TemporalPropertyStore store = TemporalPropertyStoreFactory.newPropertyStore(new File("/tmp/test-db1"));
        for(int t=10; t<1000; t+=5){
            for(long entityId=0; entityId<10000; entityId++) {
                if(entityId==0 && t==10) {
                    set(store, entityId, 2, 1, 2, 3);
                    set(store, entityId, 2, 3, 3, 4);
                    set(store, entityId, 2, 4, 5, 5);
//                    set(store, entityId, 2, 6, 7);
                    set(store, entityId, 2, 8, 9, 6);
                }
                set(store, entityId, 2, t, t+4, t);
            }
        }
        store.shutDown();
    }

    private void set(TemporalPropertyStore store, long entityId, int propId, int timeStart, int timeEnd) {
        Slice valSlice = Slices.allocate(0);
        store.setProperty(new TimeIntervalKey(new EntityPropertyId(entityId, propId), new TimePointL(timeStart), new TimePointL(timeEnd), ValueType.INVALID), valSlice);
    }

    private void set(TemporalPropertyStore store, long entityId, int propId, int timeStart, int timeEnd, int value) {
        Slice valSlice = Slices.allocate(8);
        valSlice.output().writeInt(value);
        store.setProperty(new TimeIntervalKey(new EntityPropertyId(entityId, propId), new TimePointL(timeStart), new TimePointL(timeEnd), ValueType.INT), valSlice);
    }

    @Test
    public void functionValidate() throws Throwable {
        TemporalPropertyStore store = TemporalPropertyStoreFactory.newPropertyStore(new File("/tmp/test-db1"));
        long entityId=0;
        for(int t=0; t<10; t++) {
            get(store, entityId, 2, t);
        }
        store.shutDown();
    }

    private void get(TemporalPropertyStore store, long entityId, int propId, int time) {
        Slice val = store.getPointValue(entityId, propId, new TimePointL(time));
        System.out.println(val==null ? null : (val.length()==0 ? '-' : val.input().readInt()));
    }
}
