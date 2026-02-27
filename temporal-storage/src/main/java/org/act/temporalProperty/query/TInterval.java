package org.act.temporalProperty.query;

import com.google.common.base.Preconditions;

import java.util.Objects;

/**
 * Created by song on 2018-05-09.
 */
public abstract class TInterval<TIME_POINT extends TPoint<TIME_POINT>>
{
    private TIME_POINT from; // inclusive
    private TIME_POINT to;  // inclusive

    public TInterval( TIME_POINT startTime, TIME_POINT endTime )
    {
        assert startTime.compareTo( endTime ) <= 0 : "invalid time interval! got ["+startTime+", "+ endTime+"]";
        this.from = startTime;
        this.to = endTime;
    }

    public TIME_POINT start()
    {
        return from;
    }

    public TIME_POINT end()
    {
        return to;
    }

    /**
     * time axis: -------------|-----------> time ascend
     * this interval:   [------]
     * @param time:            (* ... )
     * @return true if
     */
    public boolean lessThan( TIME_POINT time )
    {
        return time.compareTo( to ) > 0;
    }

    public boolean greaterOrEq( TIME_POINT time )
    {
        return from.compareTo( time ) >= 0;
    }

    public boolean span( TIME_POINT time )
    {
        return from.compareTo( time ) < 0 && time.compareTo( to ) <= 0;
    }

    public boolean span( TIME_POINT start, TIME_POINT end )
    {
        return from.compareTo( start ) < 0 && start.compareTo( end ) <= 0 && end.compareTo( to ) <= 0;
    }

    public boolean between( TIME_POINT start, TIME_POINT end )
    {
        return start.compareTo( from ) <= 0 && to.compareTo( end ) <= 0;
    }

    public abstract TInterval<TIME_POINT> changeEnd( TIME_POINT newEnd );

    public abstract TInterval<TIME_POINT> changeStart( TIME_POINT newStart );

    @Override
    public boolean equals( Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }
        TInterval that = (TInterval) o;
        return from == that.from;
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode( from );
    }

    @Override
    public String toString()
    {
        return "TimeInterval{start=" + from + ", end=" + to + '}';
    }

    public abstract int byteCount();
}
