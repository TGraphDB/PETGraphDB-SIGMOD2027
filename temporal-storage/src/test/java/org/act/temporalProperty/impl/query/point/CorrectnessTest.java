package org.act.temporalProperty.impl.query.point;

import org.act.temporalProperty.TemporalPropertyStore;
import org.act.temporalProperty.meta.ValueContentType;
import org.act.temporalProperty.util.StoreBuilder;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by song on 2018-03-27.
 */
public class CorrectnessTest {
    private static Logger log = LoggerFactory.getLogger(CorrectnessTest.class);
    private String dbDir(){
        if(SystemUtils.IS_OS_WINDOWS){
            return "temporal.property.test";
        }else{
            return "/tmp/temporal.property.test";
        }
    }

    @Test // to fail
    public void notBufferFileError() throws Throwable {
        StoreBuilder stBuilder = new StoreBuilder(dbDir(), true);
        TemporalPropertyStore store = stBuilder.store();
        store.createProperty(0, ValueContentType.INT);
        for(long i=0; i<999999; i++) {
            StoreBuilder.setIntProperty(store, (int)i, 1, 0, (int)i);
        }
        for(long i=0; i<999999; i++) {
            StoreBuilder.setIntProperty(store, (int)i, 2, 0, (int)i);
        }
//        Slice val = store.getPointValue(1, 0, 4);
//        log.debug("{}", val.getInt(0)==4);
        store.shutDown();
    }

    @Test // throws Assertion Error in TableBuilder "key must be greater than last key";
    public void secondStableBuildFailed() throws Throwable {
        StoreBuilder stBuilder = new StoreBuilder(dbDir(), true);
        TemporalPropertyStore store = stBuilder.store();
        store.createProperty(0, ValueContentType.INT);
        for(int i=0; i<999999999; i++) {
            StoreBuilder.setIntProperty(store, i, 1, 0, i);
        }
//        Slice val = store.getPointValue(1, 0, 4);
//        log.debug("{}", val.getInt(0)==4);
        store.shutDown();
    }

}
