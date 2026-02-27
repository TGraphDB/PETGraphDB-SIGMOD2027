package org.act.temporalProperty.index.aggregation;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import org.act.temporalProperty.exception.TPSRuntimeException;
import org.act.temporalProperty.query.TimeInterval;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.TimeIntervalUtil;
import org.apache.commons.lang3.time.DateUtils;

import java.util.*;

/**
 * (系统默认的TimeGroupBuilder)
 * 底层时间为Unix时间戳（毫秒，long类型）时的划分机制，可以自定义间隔大小
 * 每个TimeGroup用该group的开始时间作为ID
 * 计算指定时间区间上建立索引分组的每组的起始时间及组ID。
 * 例如，给定start, end为某天的8:04:33到8:23:15, timeUnit为分钟, every=7, 则切分结果为:
 * [groupId=0]  8:05:00~8:11:59
 * [groupId=1]  8:12:00~8:18:59
 * Aggregation索引会在每个group上分别计算索引值, 并在查询时读取被查询时间区间完全覆盖的group的索引值以加速查询.
 * 返回结果为TreeMap{<8:05:00的时间戳, 0>, <8:12:00的时间戳, 1>}
 * 其中8:05:00是时间轴按分钟划分后, start之后(>=)group开始的最小时间.(通过DateUtils.round函数实现)
 * 8:19:00=8:19:00-0:0:1是时间轴按分钟划分后, end之前的最大时间时间减一秒.
 * 由于every=7, 所以每连续的7分钟作为一组
 * timeUnit can be Calendar.SECOND|HOUR|DAY|WEEK|SEMI_MONTH|MONTH|YEAR
 */

public class UnixTimestampTimeGroupBuilder extends TimeGroupBuilder {
    private static Set<Integer> allowedTimeUnit =
            new HashSet<>( Arrays.asList( Calendar.YEAR, Calendar.MONTH, Calendar.DATE, Calendar.HOUR, Calendar.MINUTE, Calendar.SECOND ) );

    private final int tEvery;
    private final int timeUnit;
    private final Calendar startPoint;

    public UnixTimestampTimeGroupBuilder(TimePointL timeStart, TimePointL timeEnd, int tEvery, int timeUnit )
    {
        super(timeStart, timeEnd);
        Preconditions.checkArgument( allowedTimeUnit.contains( timeUnit ), "invalid timeUnit!" );
        this.tEvery = tEvery;
        this.timeUnit = timeUnit;
        Calendar tmp = Calendar.getInstance();
        tmp.setTimeInMillis( timeStart.val() * 1000 - 1 );
        this.startPoint = DateUtils.ceiling(tmp, timeUnit);//索引的起始时间
//        System.out.println("UnixTimestampTimeGroupBuilder startTime: "+ this.startPoint.getTime());
    }

    @Override
    public NavigableSet<TimePointL> calcNewGroup(TimePointL min, TimePointL max )
    {
        TimeInterval common = TimeIntervalUtil.overlapInterval(min, max, timeStart, timeEnd);
        long beginT = common.start().val();
        long finishT = common.end().val();
        TreeSet<TimePointL> set = new TreeSet<>();

        Calendar intervalStart = (Calendar) startPoint.clone();
        while( intervalStart.getTimeInMillis() < beginT * 1000 ) {
            intervalStart.add( timeUnit, tEvery );
        }//找到了起点：intervalStart现在是第一个大于等于beginT的valid时间点
        while( intervalStart.getTimeInMillis() < finishT * 1000 ){//注意这里是小于无等于因为intervalStart是TimeGroup的开始时间而不是结束时间
            set.add(new TimePointL(intervalStart.getTimeInMillis() / 1000));
            intervalStart.add( timeUnit, tEvery );
        }
        return set;
    }
}
