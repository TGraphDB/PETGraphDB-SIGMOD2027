package org.act.temporalProperty.impl;

import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.util.Slice;

/**
 * Note: after call seekFloor(), seekCeil(), or seekToFirst(), the result of hasNext() and peek() may change.
 * Created by song on 2018-03-29.
 * Edit by sjh @2019-10-11
 */
public interface SearchableIterator extends PeekingIterator<InternalEntry> {
    /**
     * Repositions the iterator to the beginning of list of element.
     * so the next Entry is the first element packed in this list.
     */
    void seekToFirst();

    /**
     * Repositions the iterator inner pointer so the key of the next Entry is the latest key smaller or equal to the specified targetKey. (similar to FLOOR operator)
     * if data list is not empty, then after call this method, hasNext() always return true.
     * @return true if there exists a key smaller than or equal to targetKey,
     * false if the smallest key is larger than targetKey, thus the effect is same as {@code seekToFirst()}
     *
     * Example:
     * 1.if data list is {3:B, 5:C}, then
     * after call seekFloor(2)=FALSE, peek() and next() return (3:B)
     * after call seekFloor(3)=true, peek() and next() return entry (3:B)
     * after call seekFloor(4)=true, peek() and next() return entry (3:B)
     * after call seekFloor(5)=true, peek() and next() return entry (5:C)
     * after call seekFloor(6)=true, peek() and next() return entry (5:C)
     * 2. if data list is { } (empty), then seekFloor(n) always return false
     *
     */
    boolean seekFloor(InternalKey targetKey);

    /**
     * It seems there is no need for this method.
     * Repositions the iterator inner pointer so the key of the next Entry is the smallest key equal to or larger than the specified targetKey. (similar to Ceil operator)
     * if data key is {3:B, 5:C}, then
     * after call seekCeil(0) or seekCeil(1) or seekCeil(2), peek() and next() return (3:B)
     * after call seekCeil(3), peek() and next() return entry (3:B)
     * after call seekCeil(4), peek() and next() return entry (5:C)
     * after call seekCeil(5), peek() and next() return entry (5:C)
     * after call seekCeil(6), peek() and next() return entry (5:C)
     */
//    void seekCeil(InternalKey targetKey);
}
