package org.act.temporalProperty.impl;

import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.util.Slice;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 在时间段查询中使用的回调函数
 *
 */
public interface IntervalIterator extends PeekingIterator<Entry<TimeIntervalKey,Slice>>
{

}
