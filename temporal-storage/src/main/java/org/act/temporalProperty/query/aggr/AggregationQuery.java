package org.act.temporalProperty.query.aggr;

import com.google.common.collect.ImmutableMap;
import org.act.temporalProperty.impl.InternalEntry;
import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.index.IndexValueType;
import org.act.temporalProperty.query.range.TimeRangeQuery;
import org.act.temporalProperty.util.Slice;

import java.util.Comparator;
import java.util.LinkedList;

/**
 * Created by song on 2018-04-05.
 */
public interface AggregationQuery extends TimeRangeQuery
{
    LinkedList<Long> time = new LinkedList<>();
    int MIN = 0;
    int MAX = 1;

    AggregationQuery Min = new AggregationQuery()
    {
        private Comparator<Slice> cp;
        private Slice min;

        @Override
        public void setValueType(String valueType )
        {
            cp = ValueGroupingMap.getComparator( IndexValueType.convertFrom( valueType ) );
        }

        @Override
        public boolean onNewEntry(InternalEntry entry )
        {
            InternalKey key = entry.getKey();
            if ( key.getValueType().isValue() )
            {
                if ( min == null || cp.compare( entry.getValue(), min ) < 0 )
                { min = entry.getValue(); }
            }
            return true;
        }

        @Override
        public Object onReturn()
        {
            return min;
        }
    };

    AggregationQuery Max = new AggregationQuery()
    {
        private Comparator<Slice> cp;
        private Slice max;

        @Override
        public void setValueType(String valueType )
        {
            cp = ValueGroupingMap.getComparator( IndexValueType.convertFrom( valueType ) );
        }

        @Override
        public boolean onNewEntry(InternalEntry entry )
        {
            InternalKey key = entry.getKey();
            if ( key.getValueType().isValue() )
            {
                if ( max == null || cp.compare( entry.getValue(), max ) > 0 )
                {
                    max = entry.getValue();
                }
            }
            return true;
        }

        @Override
        public Object onReturn()
        {
            return max;
        }
    };

    AggregationQuery MinMax = new AggregationQuery()
    {
        private Comparator<Slice> cp;
        private Slice min;
        private Slice max;

        @Override
        public void setValueType(String valueType )
        {
            cp = ValueGroupingMap.getComparator( IndexValueType.convertFrom( valueType ) );
        }

        @Override
        public boolean onNewEntry(InternalEntry entry )
        {
            InternalKey key = entry.getKey();
            if ( key.getValueType().isValue() )
            {
                if ( min == null || cp.compare( entry.getValue(), min ) < 0 )
                { min = entry.getValue(); }

                if ( max == null || cp.compare( entry.getValue(), max ) > 0 )
                {
                    max = entry.getValue();
                }
            }
            return true;
        }

        @Override
        public Object onReturn()
        {
            return ImmutableMap.of( MIN, min, MAX, max );
        }
    };


    long[] cnt = {0};
}
