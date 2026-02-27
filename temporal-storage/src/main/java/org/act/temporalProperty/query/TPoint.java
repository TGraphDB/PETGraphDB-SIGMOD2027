package org.act.temporalProperty.query;

/**
 * Created by song on 2018-05-09.
 */
public interface TPoint<T extends TPoint> extends Comparable<T>
{
    T pre();

    T next();

    boolean isNow();

    boolean isInit();
}
