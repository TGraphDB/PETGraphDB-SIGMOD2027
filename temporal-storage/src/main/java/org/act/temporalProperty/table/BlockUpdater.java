//package org.act.temporalProperty.table;
//
//import static org.act.temporalProperty.util.SizeOf.SIZE_OF_INT;
//
//import java.util.Comparator;
//import java.util.Map.Entry;
//
//import org.act.temporalProperty.impl.InternalKey;
//import org.act.temporalProperty.impl.ValueType;
//import org.act.temporalProperty.util.Slice;
//import org.act.temporalProperty.util.SliceInput;
//import org.act.temporalProperty.util.SliceOutput;
//import org.act.temporalProperty.util.Slices;
//import org.act.temporalProperty.util.VariableLengthQuantity;
//
//import com.google.common.base.Preconditions;
//
//public class BlockUpdater
//{
//    private SliceInput dataInput;
//    private Slice data;
//    private Slice restartPositions;
//    private int restartCount;
//    private boolean good2go = true;
//    private Comparator<Slice> comparator;
//
//    public BlockUpdater( Block dataBlock )
//    {
//        this.data = dataBlock.data;
//        this.restartPositions = dataBlock.restartPositions;
//        this.dataInput = this.data.input();
//        this.comparator = TableComparator.instance();
//        this.restartCount = restartPositions.length()/SIZE_OF_INT;
//    }
//
//    public TableUpdateResult update( Slice id, int time, int valueLength, ValueType valueType, Slice value )
//    {
//        if( !good2go )
//            return new TableUpdateResult( false, 0, null );
//        else
//        {
//            InternalKey key = new InternalKey( id, time, valueLength, valueType );
//            Slice lookupKey = key.encode();
//            Entry<Slice,Slice> entry = searchPostion( lookupKey );
//            InternalKey resultKey = new InternalKey( entry.getKey() );
//            if( !resultKey.getId().equals( id ) )
//                return new TableUpdateResult( false, 0, null );
//            if( resultKey.getStartTime() != time )
//                return new TableUpdateResult( false, 0, null );
//            if( entry.getValue().length() != value.length() )
//                return new TableUpdateResult( false, 0, null );
//            Slice record = constructRecord( lookupKey, value );
//            return new TableUpdateResult( true, this.dataInput.position(), record );
//        }
//    }
//
//    private Slice constructRecord( Slice key, Slice value )
//    {
//        SliceOutput output = Slices.allocate( 20 + value.length() + 12 ).output();
//
//        VariableLengthQuantity.writeVariableLengthInt(0, output);
//        VariableLengthQuantity.writeVariableLengthInt(key.length(), output);
//        VariableLengthQuantity.writeVariableLengthInt(value.length(), output);
//
//        // write non-shared key bytes
//        output.writeBytes(key, 0, key.length());
//
//        // write value bytes
//        output.writeBytes(value, 0, value.length());
//
//        return output.slice();
//    }
//
//    private void seekToRestartPosition( int position )
//    {
//        Preconditions.checkPositionIndex( position, restartCount );
//        int offset = restartPositions.getInt(position * SIZE_OF_INT);
//        dataInput.setPosition(offset);
//    }
//
//    private Entry<Slice,Slice> searchPostion( Slice lookupKey )
//    {
//        Entry<Slice,Slice> nextEntry;
//        if (restartCount == 0) {
//            dataInput.setPosition(0);
//            nextEntry = readEntry(dataInput);
//            return nextEntry;
//        }
//
//        int left = 0;
//        int right = restartCount - 1;
//
//        // binary search restart positions to find the restart position immediately before the lookupKey
//        while (left < right) {
//            int mid = (left + right + 1) / 2;
//
//            seekToRestartPosition(mid);
//            nextEntry = readEntry(dataInput);
//            if (comparator.compare(nextEntry.getKey(), lookupKey) < 0) {
//                // key at mid is smaller than lookupKey.  Therefore all restart
//                // blocks before mid are uninteresting.
//                left = mid;
//            }
//            else {
//                // key at mid is greater than or equal to lookupKey.  Therefore
//                // all restart blocks at or after mid are uninteresting.
//                right = mid - 1;
//            }
//        }
//
//        // linear search (within restart block) for first key greater than or equal to lookupKey
//        // change to search for first key less than or equal to targetKey
//        seekToRestartPosition( left );
//        int prePos = dataInput.position();
//        nextEntry = readEntry(dataInput);
//        while( this.comparator.compare( nextEntry.getKey(), lookupKey ) < 0)
//        {
//            prePos = dataInput.position();
//            nextEntry = readEntry( dataInput );
//        }
//        dataInput.setPosition( prePos );
//        return nextEntry;
//    }
//
//    private Entry<Slice,Slice> readEntry( SliceInput data )
//    {
//        Preconditions.checkNotNull(data, "data is null");
//
//        // read entry header
//        int sharedKeyLength = VariableLengthQuantity.readVariableLengthInt(data);
//        int nonSharedKeyLength = VariableLengthQuantity.readVariableLengthInt(data);
//        int valueLength = VariableLengthQuantity.readVariableLengthInt(data);
//
//        Preconditions.checkArgument( sharedKeyLength == 0,"sharedKeyLength should be 0" );
//
//        // read key
//        Slice key = Slices.allocate(sharedKeyLength + nonSharedKeyLength);
//        SliceOutput sliceOutput = key.output();
//
//        sliceOutput.writeBytes(data, nonSharedKeyLength);
//
//        // read value
//        Slice value = data.readSlice(valueLength);
//
//        return new Entry<Slice,Slice>()
//                {
//
//                    @Override
//                    public Slice getKey()
//                    {
//                        return key;
//                    }
//
//                    @Override
//                    public Slice getValue()
//                    {
//                        return value;
//                    }
//
//                    @Override
//                    public Slice setValue( Slice value )
//                    {
//                        return value;
//                    }
//
//                };
//    }
//}
