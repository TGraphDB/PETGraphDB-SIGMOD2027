package org.act.temporalProperty.index.value;

import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Slice;

/**
 * Created by song on 2018-01-19.
 */
public class TimePointEntry {
    private int propertyId;
    private long entityId;
    private TimePointL timePoint;
    private Slice value;

    public TimePointEntry(int propertyId, long entityId, TimePointL timePoint, Slice value) {
        this.propertyId = propertyId;
        this.entityId = entityId;
        this.timePoint = timePoint;
        this.value = value;
    }

    public int getPropertyId() {
        return propertyId;
    }

    public long getEntityId() {
        return entityId;
    }

    public TimePointL getTimePoint() {
        return timePoint;
    }

    public Slice getValue() {
        return value;
    }
}
