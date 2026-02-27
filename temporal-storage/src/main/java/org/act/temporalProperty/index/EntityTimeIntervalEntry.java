package org.act.temporalProperty.index;

import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Slice;

/**
 * Created by song on 2018-01-27.
 */
public class EntityTimeIntervalEntry {
    private final long entityId;
    private final TimePointL start;
    private final TimePointL end;
    private final Slice value;
    public EntityTimeIntervalEntry(long entityId, TimePointL start, TimePointL end, Slice value){
        this.entityId = entityId;
        this.start = start;
        this.end = end;
        this.value = value;
    }

    public long entityId(){
        return entityId;
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
