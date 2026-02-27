package org.act.temporalProperty.query.range;

import org.act.temporalProperty.impl.InternalEntry;
import org.act.temporalProperty.meta.ValueContentType;

/**
 * 在时间段查询中使用的回调函数
 *
 */
public interface InternalEntryRangeQueryCallBack
{
    /**
     * This method is called before the range query to tell you the type of values
     * @param valueType see {@link ValueContentType} for details.
     */
    void setValueType(String valueType);
    // return true to continue search, false to stop loop.
    boolean onNewEntry(InternalEntry entry);
    Object onReturn();
}
