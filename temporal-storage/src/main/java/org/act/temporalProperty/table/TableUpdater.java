//package org.act.temporalProperty.table;
//
//import java.util.Comparator;
//
//import org.act.temporalProperty.impl.InternalKey;
//import org.act.temporalProperty.impl.ValueType;
//import org.act.temporalProperty.util.Slice;
//
//public class TableUpdater
//{
//    private Table table;
//    private IndexBlock indexBlock;
//    private Comparator<Slice> comparator;
//
//    public TableUpdater( Table t )
//    {
//        this.table = t;
//        this.indexBlock = this.table.indexBlock;
//        this.comparator = TableComparator.instance();
//    }
//
//
//    public boolean update( Slice id, int time, int valueLength, ValueType valueType, Slice value )
//    {
//        InternalKey key = new InternalKey( id, time, valueLength, valueType );
//        Slice lookupKey = key.encode();
//        BlockIterator indexIterator = this.indexBlock.iterator();
//        indexIterator.seek( lookupKey );
//        if( !indexIterator.hasNext() )
//            return false; //Not found the target record
//        BlockEntry blockEntry = indexIterator.next();
//        BlockEntry pre = blockEntry;
//        if( this.comparator.compare( blockEntry.getKey(), lookupKey ) == 0 )
//        {
//            blockEntry = indexIterator.next();
//        }
//        Block dataBlock;
//        long position;
//        if( null == blockEntry )
//        {
//            dataBlock = this.table.openBlock( pre.getValue() );
//            position = BlockHandle.readBlockHandle(pre.getValue().input()).getOffset();
//        }
//        else
//        {
//            dataBlock = this.table.openBlock( blockEntry.getValue() );
//            position = BlockHandle.readBlockHandle(blockEntry.getValue().input()).getOffset();
//        }
//
//        BlockUpdater blockUpdater = new BlockUpdater( dataBlock );
//        TableUpdateResult result = blockUpdater.update( id, time, valueLength, valueType, value );
//        if( !result.isSuccess() )
//            return false;
//        return this.table.update( position ,result );
//    }
//
//}
