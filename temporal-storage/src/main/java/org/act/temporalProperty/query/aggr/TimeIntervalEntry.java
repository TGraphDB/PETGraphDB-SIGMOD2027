package org.act.temporalProperty.query.aggr;

import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Slice;

/**
 * Created by song on 2018-01-27.
 */
public class TimeIntervalEntry {
    private final TimePointL start;
    private final TimePointL end;
    private final Slice value;
    public TimeIntervalEntry(TimePointL start, TimePointL end, Slice value){
        this.start = start;
        this.end = end;
        this.value = value;
    }

    public TimePointL start(){
        return this.start;
    }

    public TimePointL end(){
        return this.end;
    }

    public Slice value(){
        return this.value;
    }
}
