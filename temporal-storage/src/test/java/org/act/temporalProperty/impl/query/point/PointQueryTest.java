package org.act.temporalProperty.impl.query.point;

import org.act.temporalProperty.TemporalPropertyStore;
import org.act.temporalProperty.meta.ValueContentType;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.StoreBuilder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by song on 2018-01-24.
 */
public class PointQueryTest {
    private static Logger log = LoggerFactory.getLogger(PointQueryTest.class);
    private static String dbDir = "/tmp/temporal.property.test";
    private TemporalPropertyStore store;

    @Test
    public void simple() throws Throwable {
        StoreBuilder stBuilder = new StoreBuilder(dbDir, true);
        store = stBuilder.store();
        store.createProperty(0, ValueContentType.INT);
        StoreBuilder.setIntProperty(store, 0, 1, 0, 3);
        Slice val = store.getPointValue(1, 0, new TimePointL(4));
        log.debug("{}", val.getInt(0)==4);
    }

    @Test
    public void test2fail() throws Throwable {
        StoreBuilder stBuilder = new StoreBuilder(dbDir, true);
        store = stBuilder.store();
        store.createProperty(0, ValueContentType.INT);
        for(long entityId=1; entityId<5; entityId++){
            for(int time=0; time<Integer.MAX_VALUE/1000-6; time+=5){
                StoreBuilder.setIntProperty(store, time, entityId, 0, time);
            }
            log.debug("version {} write finish", entityId);
        }
        Slice val = store.getPointValue(1, 0, new TimePointL(4));
        log.debug("{}", val.getInt(0)==4);
    }

    @Test
    public void test2fail0() throws Throwable {
        StoreBuilder stBuilder = new StoreBuilder(dbDir, true);
        store = stBuilder.store();
        store.createProperty(0, ValueContentType.INT);
        for(int time=10; time<Integer.MAX_VALUE/10000-6; time+=5){
            for(long entityId=1; entityId<5; entityId++){
                StoreBuilder.setIntProperty(store, time, entityId, 0, time);
            }
        }
        Slice val = store.getPointValue(1, 0, new TimePointL(9));
        if(val!=null) log.debug("{}", val.getInt(0));
        store.shutDown();
    }

    @Test
    public void test2fail2() throws Throwable {
        StoreBuilder stBuilder = new StoreBuilder(dbDir, true);
        store = stBuilder.store();
        for(int proId=1; proId<5; proId++){
            store.createProperty(proId, ValueContentType.INT);
            for(int entity=0; entity<Integer.MAX_VALUE/1000-6; entity+=5){
                StoreBuilder.setIntProperty(store, 0, entity, proId,  0);
            }
            for(int entity=0; entity<Integer.MAX_VALUE/1000-6; entity+=3){
                StoreBuilder.setIntProperty(store, 10, entity, proId, 10);
            }
            log.debug("version {} write finish", proId);
        }
        Slice val = store.getPointValue(0, 3, new TimePointL(4)); // bug, throws NoSuchElementException
        log.debug("{}", val.getInt(0)); // should be 0
    }

    @Test
    public void javaForNullTest(){
        Object[] arr = new Object[4];
        arr[0] = new Object();
        arr[1] = null;
        arr[2] = new Object();
        arr[3] = null;
        arr[4] = null;
        for(Object obj : arr){
            log.debug("obj: {}", obj);
        }
        List<Object> list = new ArrayList<>();
        list.add(new Object());
        list.add(null);
        list.add(new Object());
        list.add(null);
        list.add(null);
        for(Object obj : list){
            log.debug("obj: {}", obj);
        }
    }
}
