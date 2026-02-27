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
import java.util.Map.Entry;

import org.act.temporalProperty.util.AbstractSeekingIterator;
import org.act.temporalProperty.util.Slice;

public final class TableIterator
        extends AbstractSeekingIterator<Slice, Slice>
{
    private final Table table;
    private final BlockIterator blockIterator;
    private BlockIterator current;
    private Comparator<Slice> comparator; 

    public TableIterator(Table table, BlockIterator blockIterator)
    {
        this.table = table;
        this.blockIterator = blockIterator;
        current = null;
        this.comparator = table.comparator;
    }

    @Override
    protected void seekToFirstInternal()
    {
        // reset index to before first and clear the data iterator
        blockIterator.seekToFirst();
        current = null;
    }

    @Override
    protected void seekInternal(Slice targetKey)
    {
        // seek the index to the block containing the key
        blockIterator.seek(targetKey);

        // if indexIterator does not have a next, it mean the key does not exist in this iterator
        if (blockIterator.hasNext()) {
            // seek the current iterator to the key
            BlockEntry pre = null;
            if( this.comparator.compare( blockIterator.peek().getKey(), targetKey) == 0 )
            {
                pre = blockIterator.peek();
                blockIterator.next();
            }
            current = getNextBlock();
            if( null == current )
                current = getBlockByBlockEntry( pre );
            current.seek(targetKey);
        }
        else {
            current = null;
        }
    }

    private BlockIterator getBlockByBlockEntry( BlockEntry entry )
    {
        Slice blockHandle = entry.getValue();
        Block dataBlock = table.openBlock(blockHandle);
        return dataBlock.iterator();
    }

    @Override
    protected Entry<Slice, Slice> getNextElement()
    {
        // note: it must be here & not where 'current' is assigned,
        // because otherwise we'll have called inputs.next() before throwing
        // the first NPE, and the next time around we'll call inputs.next()
        // again, incorrectly moving beyond the error.
        boolean currentHasNext = false;
        while (true) {
            if (current != null) {
                currentHasNext = current.hasNext();
            }
            if (!(currentHasNext)) {
                if (blockIterator.hasNext()) {
                    current = getNextBlock();
                }
                else {
                    break;
                }
            }
            else {
                break;
            }
        }
        if (currentHasNext) {
            return current.next();
        }
        else {
            // set current to empty iterator to avoid extra calls to user iterators
            current = null;
            return null;
        }
    }

    private BlockIterator getNextBlock()
    {
        BlockEntry entry = blockIterator.next();
        if( null == entry )
            return null;
        Slice blockHandle = entry.getValue();
        Block dataBlock = table.openBlock(blockHandle);
        return dataBlock.iterator();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("TableIterator");
        sb.append("{blockIterator=").append(blockIterator);
        sb.append(", current=").append(current);
        sb.append('}');
        return sb.toString();
    }
}
