package org.act.temporalProperty.query.aggr;

import java.util.Collection;
import java.util.Map;

/**
 * Created by song on 2018-04-01.
 */
public interface TimeIntervalEntryAggrQuery<GROUP_ID, AGGR_VALUE> {
    GROUP_ID computeGroupId(TimeIntervalEntry entry);
    AGGR_VALUE aggregate(GROUP_ID groupId, Collection<TimeIntervalEntry> groupItems);
    Object onResult(Map<GROUP_ID, AGGR_VALUE> result);
}
