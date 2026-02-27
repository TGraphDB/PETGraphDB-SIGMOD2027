/*
 * Copyright (C) 2011 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.act.temporalProperty.impl;

import com.google.common.collect.PeekingIterator;

import java.util.Map.Entry;

public interface SeekingIterator<K, V>
        extends PeekingIterator<Entry<K, V>>
{
    /**
     * Repositions the iterator so the beginning of this block.
     */
    void seekToFirst();

    /**
     * edit description by sjh @2018-01-25
     * Origin description:
     * Repositions the iterator so the key of the next BlockElement returned greater than or equal to the specified targetKey.
     * New description:  (similar to FLOOR operator)
     * if data key is 1, 3, 5, then
     * |seek | peek() and next() returns |
     * |  0  |  1  | ? not for sure
     * |  1  |  1  |
     * |  2  |  1  |
     * |  3  |  3  |
     * |  4  |  3  |
     * |  5  |  5  |
     * |  6  |  5  |
     * IMPORTANT NOTE:
     * the behavior of peek() and next() is defined by implementations. For example, a implementation may
     * only return entries whose key > 2, thus seek(0), seek(1), seek(2), seek(3) have the same effect.
     */
    void seek(K targetKey);
}
