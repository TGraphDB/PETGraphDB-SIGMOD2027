package org.act.temporalProperty.index.aggregation;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.query.TimePointL;
import org.apache.commons.lang3.time.DateUtils;

import java.util.*;

/**
 * TimeGroupBuilder包含了时间轴的划分机制。用于加速Aggregation查询。
 * TimeGroupBuilder和Aggregation索引一一对应。
 * 可以按照划分机制生成TimeGroup列表（为方便查询这里用的是一个二叉树索引了的列表：TreeSet）
 * 只有在Aggregation查询指定的时间范围内完整包含的TimeGroup可以被加速。
 * TimeGroup列表本质上是一系列首尾相接的TimeInterval。每个TimeGroup用该group的开始时间作为ID
 *
 * 注意这里的分组和Aggregation本身的分组无关，Aggregation可以按时间分组也可以不按照时间分组。
 *
 * Created by song on 2018-05-10.
 */
public abstract class TimeGroupBuilder
{
    protected final TimePointL timeStart;
    protected final TimePointL timeEnd;

    /**
     *
     * @param timeStart 索引的开始时间
     * @param timeEnd   索引结束时间
     */
    protected TimeGroupBuilder(TimePointL timeStart, TimePointL timeEnd) {
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
    }

    /**
     * 根据划分机制生成TimeGroup列表，这两个参数存在其实是为了避免生成无用的TimeGroup，比如：
     * 索引指定的时间是2010-2011年，但是索引的数据是从2010年10月1日到31日的，这样就可以只生成和10月有关的TimeGroup，前后都不要了。
     * @param min 生成TimeGroup列表的起始时刻大于等于这个时间
     * @param max 生成TimeGroup列表的最后一个TimeGroup的endTime小于等于这个时间
     * @return 生成的TimeGroup列表
     */
    public abstract NavigableSet<TimePointL> calcNewGroup(TimePointL min, TimePointL max);
}
