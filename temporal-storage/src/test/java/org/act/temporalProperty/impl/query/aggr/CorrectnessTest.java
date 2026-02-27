package org.act.temporalProperty.impl.query.aggr;

import org.act.temporalProperty.TemporalPropertyStore;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.query.aggr.TimeIntervalEntry;
import org.act.temporalProperty.meta.ValueContentType;
import org.act.temporalProperty.query.aggr.DurationStatisticAggregationQuery;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.StoreBuilder;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by song on 2018-04-02.
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
    public void countQuery() throws Throwable {
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
        log.debug("point query result {}", val.getInt(0));
        log.debug("if you see next line, then means no bug.");
        Object resultMap = queryAggr(store, 0, 1, sep*2-10, sep*2, 1);
        Map<Integer, Integer> r = (Map<Integer, Integer>) resultMap;
        store.shutDown();
    }

    private Object queryAggr(TemporalPropertyStore store, int proId, int entityId, int start, int end, int interval) {
        TimePointL s = new TimePointL(start);
        TimePointL e = new TimePointL(end);
        return store.aggregate(entityId, proId, s, e, new DurationStatisticAggregationQuery<TimePointL>(s, e) {
            @Override public void setValueType(String valueType) { }
            @Override
            public TimePointL computeGroupId(TimeIntervalEntry entry) {
                return new TimePointL(asInt(entry.value()));
            }
            @Override
            public Object onResult(Map<TimePointL, Integer> result) {
                return result;
            }
        });
    }

}
