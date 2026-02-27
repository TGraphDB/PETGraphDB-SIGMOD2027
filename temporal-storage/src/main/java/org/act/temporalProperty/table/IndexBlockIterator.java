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
package org.act.temporalProperty.table;

import java.util.Comparator;

import org.act.temporalProperty.util.Slice;

/**
 * 索引块的Iterator，使用它查询需要读取的DataBlock的位置
 *
 */
public class IndexBlockIterator extends BlockIterator
{

    public IndexBlockIterator( Slice data, Slice restartPositions, Comparator<Slice> comparator )
    {
        super( data, restartPositions, comparator );
    }

    @Override
    public BlockEntry next()
    {
        if (!hasNext()) {
            return null;
        }

        BlockEntry entry = nextEntry;

        if (!data.isReadable()) {
            nextEntry = null;
        }
        else {
            // read entry at current data position
            nextEntry = readEntry(data, nextEntry);
        }

        return entry;
    }
    
    /**
     * Repositions the iterator so the key of the next BlockElement returned greater than or equal to the specified targetKey.
     */
    @Override
    public void seek(Slice targetKey)
    {
        if (restartCount == 0) {
            return;
        }

        int left = 0;
        int right = restartCount - 1;

        // binary search restart positions to find the restart position immediately before the targetKey
        while (left < right) {
            int mid = (left + right + 1) / 2;

            seekToRestartPosition(mid);

            if (comparator.compare(nextEntry.getKey(), targetKey) < 0) {
                // key at mid is smaller than targetKey.  Therefore all restart
                // blocks before mid are uninteresting.
                left = mid;
            }
            else {
                // key at mid is greater than or equal to targetKey.  Therefore
                // all restart blocks at or after mid are uninteresting.
                right = mid - 1;
            }
        }

        // linear search (within restart block) for first key greater than or equal to targetKey
        int prePos = this.data.position();
        BlockEntry preEntry = nextEntry;
        for (seekToRestartPosition(left); nextEntry != null;) {
            if (comparator.compare(peek().getKey(), targetKey) >= 0) {
                break;
            }
            prePos = this.data.position();
            preEntry = nextEntry;
            next();
        }
        if( null == nextEntry )
        {
            nextEntry = preEntry;
            this.data.setPosition( prePos );
        }
    }

}
