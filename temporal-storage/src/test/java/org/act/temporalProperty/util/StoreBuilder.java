package org.act.temporalProperty.util;

import org.act.temporalProperty.TemporalPropertyStore;
import org.act.temporalProperty.TemporalPropertyStoreFactory;
import org.act.temporalProperty.impl.ValueType;
import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.vo.EntityPropertyId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Created by song on 2018-01-23.
 */
public class StoreBuilder {
    private static Logger log = LoggerFactory.getLogger(StoreBuilder.class);

    private final File dbDir;
    private final TemporalPropertyStore store;

    public StoreBuilder(String storePath, boolean fromScratch) throws Throwable {
        this.dbDir = new File(storePath);
        if(fromScratch){
            if(dbDir.exists()) {
                deleteAllFilesOfDir(dbDir);
            }
            dbDir.mkdir();
            store = TemporalPropertyStoreFactory.newPropertyStore(dbDir);
        }else{
            store = TemporalPropertyStoreFactory.newPropertyStore(dbDir);
        }
    }

    public TemporalPropertyStore store(){
        return this.store;
    }

    public static void setIntProperty(TemporalPropertyStore store, int time, long entityId, int propertyId, int value) {
        EntityPropertyId id = new EntityPropertyId(entityId, propertyId);
        Slice val = new Slice(4);
        val.setInt( 0, value );
        store.setProperty( new TimeIntervalKey( id, new TimePointL(time), TimePointL.Now, ValueType.INT), val );
    }

    private static Slice getIdSlice(long entityId, int propertyId) {
        Slice result = new Slice(12);
        result.setLong(0, entityId);
        result.setInt(8, propertyId);
        return result;
    }

    private static void deleteAllFilesOfDir(File path) {
        if (!path.exists())
            return;
        if (path.isFile()) {
            path.delete();
            return;
        }
        File[] files = path.listFiles();
        for (int i = 0; i < files.length; i++) {
            deleteAllFilesOfDir(files[i]);
        }
        path.delete();
    }
}
