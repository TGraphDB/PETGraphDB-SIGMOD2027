package org.neo4j.kernel.temporal;

import org.act.temporalProperty.query.range.InternalEntryRangeQueryCallBack;
import org.act.temporalProperty.query.range.TimeRangeQuery;
import org.neo4j.graphdb.temporal.TimePoint;

/**
 * Supported operations:
 * - entity time point read
 * - entity time range read
 * - entity aggregation index read
 *
 * Created by song on 2018-05-04.
 */
public class TemporalPropertyReadOperation
{
    public static int POINT = 0;
    public static int RANGE = 1;
    public static int AGGR_INDEX = 2;

    private int type = -1;

    private long entityId;
    private int proId;
    private TimePoint start;
    private TimePoint end;
    private long indexId = -1;
    private InternalEntryRangeQueryCallBack callBack;

    // constructor for entity time point query
    public TemporalPropertyReadOperation( long entityId, int proId, TimePoint time )
    {
        this.entityId = entityId;
        this.proId = proId;
        this.start = time;
        this.type = POINT;
    }

    // constructor for entity time range query
    public TemporalPropertyReadOperation( long entityId, int proId, TimePoint startTime, TimePoint endTime, TimeRangeQuery callBack )
    {
        this.entityId = entityId;
        this.proId = proId;
        this.start = startTime;
        this.end = endTime;
        this.callBack = callBack;
        this.type = RANGE;
    }

    // constructor for entity index aggregation query
    public TemporalPropertyReadOperation( long entityId, int proId, TimePoint startTime, TimePoint endTime, long indexId )
    {
        this.entityId = entityId;
        this.proId = proId;
        this.start = startTime;
        this.end = endTime;
        this.indexId = indexId;
        this.type = AGGR_INDEX;
    }

    public int getProId()
    {
        return proId;
    }

    public long getEntityId()
    {
        return entityId;
    }

    public boolean isPointQuery(){return type==POINT;}

    public boolean isRangeQuery(){return type==RANGE;}

    public boolean isIndexQuery(){return type==AGGR_INDEX;}

    public TimePoint getStart()
    {
        return start;
    }

    public TimePoint getEnd()
    {
        return end;
    }

    public InternalEntryRangeQueryCallBack callBack()
    {
        return callBack;
    }

    public long getIndexId()
    {
        return indexId;
    }
}
