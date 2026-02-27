package org.act.temporalProperty.util;

import static org.act.temporalProperty.util.SizeOf.SIZE_OF_INT;

import java.util.Comparator;
import java.util.NoSuchElementException;

import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.impl.SeekingIterator;
import org.act.temporalProperty.table.BlockEntry;

import com.google.common.base.Preconditions;

public class BlockLatestValueIterator implements SeekingIterator<Slice, Slice>
{

    protected final SliceInput data;
    protected final Slice restartPositions;
    protected final int restartCount;
    protected final Comparator<Slice> comparator;

    protected BlockEntry nextEntry;

    public BlockLatestValueIterator(Slice data, Slice restartPositions, Comparator<Slice> comparator)
    {
        Preconditions.checkNotNull(data, "data is null");
        Preconditions.checkNotNull(restartPositions, "restartPositions is null");
        Preconditions.checkArgument(restartPositions.length() % SIZE_OF_INT == 0, "restartPositions.readableBytes() must be a multiple of %s", SIZE_OF_INT);
        Preconditions.checkNotNull(comparator, "comparator is null");

        this.data = data.input();

        this.restartPositions = restartPositions.slice();
        restartCount = this.restartPositions.length() / SIZE_OF_INT;

        this.comparator = comparator;
        
        seekToFirst();
    }

    @Override
    public boolean hasNext()
    {
        return nextEntry != null;
    }

    @Override
    public BlockEntry peek()
    {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return nextEntry;
    }

    @Override
    public BlockEntry next()
    {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        BlockEntry entry = nextEntry;

        if (!data.isReadable()) {
            nextEntry = null;
        }
        else {
            // read entry at current data position
            nextEntry = readEntry(data, nextEntry);
            if( null != entry )
            {
                InternalKey nextKey = InternalKey.decode( nextEntry.getKey() );
                InternalKey preKey = InternalKey.decode( entry.getKey() );
                if( nextKey.getId().equals( preKey.getId() ) && hasNext() )
                {
                    return next();
                }
            }
        }
        return entry;
    }

    @Override
    public void remove()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Repositions the iterator so the beginning of this block.
     */
    @Override
    public void seekToFirst()
    {
        if (restartCount > 0) {
            seekToRestartPosition(0);
        }
    }

    /**
     * Repositions the iterator so the key of the next BlockElement returned greater than or equal to the specified targetKey.
     */
    @Override
    public void seek(Slice targetKey)
    {
        if (restartCount == 0) {
            data.setPosition(0);

            // clear the entries to assure key is not prefixed
            nextEntry = null;

            // read the entry
            nextEntry = readEntry(data, null);
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
        // change to search for first key less than or equal to targetKey
        seekToRestartPosition( left );
        BlockEntry entry = peek();
        int prePos = this.data.position();
        BlockEntry preEntry = peek();
        while( this.comparator.compare( entry.getKey(), targetKey ) <= 0)
        {
            prePos = this.data.position();
            preEntry = entry;
            next();
            try
            {
                entry = peek();
            }
            catch( NoSuchElementException e )
            {
                break;
            }
        }
        this.data.setPosition( prePos );
        this.nextEntry = preEntry;
    }

    /**
     * Seeks to and reads the entry at the specified restart position.
     * <p/>
     * After this method, nextEntry will contain the next entry to return, and the previousEntry will be null.
     */
    protected void seekToRestartPosition(int restartPosition)
    {
        Preconditions.checkPositionIndex(restartPosition, restartCount, "restartPosition");

        // seek data readIndex to the beginning of the restart block
        int offset = restartPositions.getInt(restartPosition * SIZE_OF_INT);
        data.setPosition(offset);

        // clear the entries to assure key is not prefixed
        nextEntry = null;

        // read the entry
        nextEntry = readEntry(data, null);
    }

    /**
     * Reads the entry at the current data readIndex.
     * After this method, data readIndex is positioned at the beginning of the next entry
     * or at the end of data if there was not a next entry.
     *
     * @return true if an entry was read
     */
    protected static BlockEntry readEntry(SliceInput data, BlockEntry previousEntry)
    {
        Preconditions.checkNotNull(data, "data is null");

        // read entry header
        int sharedKeyLength = VariableLengthQuantity.readVariableLengthInt(data);
        int nonSharedKeyLength = VariableLengthQuantity.readVariableLengthInt(data);
        int valueLength = VariableLengthQuantity.readVariableLengthInt(data);

        // read key
        Slice key = Slices.allocate(sharedKeyLength + nonSharedKeyLength);
        SliceOutput sliceOutput = key.output();
        if (sharedKeyLength > 0) {
            Preconditions.checkState(previousEntry != null, "Entry has a shared key but no previous entry was provided");
            sliceOutput.writeBytes(previousEntry.getKey(), 0, sharedKeyLength);
        }
        sliceOutput.writeBytes(data, nonSharedKeyLength);

        // read value
        Slice value = data.readSlice(valueLength);

        return new BlockEntry(key, value);
    }
}
