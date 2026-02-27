package org.act.temporalProperty.impl.query.range;

import org.act.temporalProperty.TemporalPropertyStore;
import org.act.temporalProperty.impl.InternalEntry;
import org.act.temporalProperty.meta.ValueContentType;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.query.aggr.AggregationQuery;
import org.act.temporalProperty.query.range.InternalEntryRangeQueryCallBack;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.StoreBuilder;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Assert;
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
    public void unstableNotQuery() throws Throwable {
        StoreBuilder stBuilder = new StoreBuilder(dbDir(), true);
        TemporalPropertyStore store = stBuilder.store();
        store.createProperty(0, ValueContentType.INT);
        int sep=999999;
        for(long i=0; i<sep; i++) {
            StoreBuilder.setIntProperty(store, (int)i, 1, 0, (int)i);
        }
        for(long i=sep; i<2*sep; i++) {
            StoreBuilder.setIntProperty(store, (int)i, 2, 0, (int)i);
        }
        Slice val = store.getPointValue(1,0, new TimePointL(sep*2-10));
        Assert.assertEquals( sep - 1, val.getInt( 0 ) );
        Object result = store.getRangeValue(1, 0, new TimePointL(sep*2-10), new TimePointL(sep*2), new InternalEntryRangeQueryCallBack(){
            public void setValueType(String valueType) {}
            public boolean onNewEntry(InternalEntry entry) {
                Assert.assertEquals( entry.getValue().getInt( 0 ), entry.getKey().getStartTime() );
                return true;
            }
            public Object onReturn() {
                return null;
            }
        });

        store.shutDown();
    }

    @Test
    public void simple() throws Throwable
    {
        StoreBuilder stBuilder = new StoreBuilder( dbDir(), false );
        TemporalPropertyStore store = stBuilder.store();
        try
        {
            store.getRangeValue( 5, 1, new TimePointL(0), new TimePointL(Integer.MAX_VALUE - 10), new AggregationQuery()
            {
                @Override
                public void setValueType(String valueType )
                {
                }

                @Override
                public boolean onNewEntry(InternalEntry entry )
                {
                    System.out.println( entry );
                    return true;
                }

                @Override
                public Object onReturn()
                {
                    return null;
                }
            } );
        }
        finally
        {
            store.shutDown();
        }
    }

}
