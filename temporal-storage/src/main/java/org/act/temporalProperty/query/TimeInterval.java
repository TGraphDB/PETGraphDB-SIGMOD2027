package org.act.temporalProperty.query;

import java.util.Objects;

/**
 * Created by song on 2018-05-09.
 */
public class TimeInterval extends TInterval<TimePointL>
{
    public TimeInterval( long startTime, long endTime )
    {
        super( new TimePointL( startTime ), new TimePointL( endTime ) );
    }

    public TimeInterval( TimePointL startTime )
    {
        super( startTime , TimePointL.Now );
    }

    public TimeInterval( TimePointL startTime, TimePointL endTime )
    {
        super( startTime , endTime );
    }

    public long from()
    {
        return start().val();
    }

    public long to()
    {
        return end().val();
    }

    @Override
    public TimeInterval changeEnd( TimePointL newEnd )
    {
        return new TimeInterval( start(), newEnd );
    }

    @Override
    public TimeInterval changeStart( TimePointL newStart )
    {
        return new TimeInterval( newStart, end() );
    }

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
        TimeInterval that = (TimeInterval) o;
        return Objects.equals(start(), that.start()) && Objects.equals(end(), that.end());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( start(), end() );
    }

    @Override
    public String toString()
    {
        return "TimeInterval[" + start() + ", " + end() + ']';
    }

    @Override
    public int byteCount() {
        return 24;
    }

}
