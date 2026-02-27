package org.act.temporalProperty.util;

import org.act.temporalProperty.helper.AbstractSearchableIterator;
import org.act.temporalProperty.impl.InternalEntry;
import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.impl.SearchableIterator;
import org.act.temporalProperty.query.TimePointL;

/**
 * 在生成新的StableFile的时候，需要把上一个tableFile的每个动态属性的最新的值加入到新的文件中，这个类就是提取StableFile中每个动态属性的最近值的工具
 */
public class TableLatestValueIterator extends AbstractSearchableIterator
{
    private SearchableIterator in;
    private InternalEntry first = null;
    private InternalEntry second = null;
    
    public TableLatestValueIterator(SearchableIterator iterator) {
        this.in = iterator;
        if(in.hasNext()) first=in.next();
        if(in.hasNext()) second=in.next();
    }
    @Override
    protected InternalEntry computeNext() {
        while(first !=null && second !=null){
            if( ! first.getKey().getId().equals(second.getKey().getId())){
                return shift2next();
            }else{
                shift2next();
            }
        }
        if(first !=null){ // second==null
            return shift2next();
        }else{
            return endOfData();
        }
    }

    private InternalEntry shift2next() {
        InternalEntry tmp = first;
        first = second;
        if(in.hasNext()){
            second = in.next();
        }else{
            second = null;
        }
        return tmp;
    }

    @Override
    public void seekToFirst() { throw new UnsupportedOperationException(); }

    @Override
    public boolean seekFloor(InternalKey targetKey ) { throw new UnsupportedOperationException(); }

    @Override
    public String toString() {
        return "TableLatestValueIterator{" +
                "iterator=" + in +
                ", first=" + first +
                ", second=" + second +
                '}';
    }

    /**
     * By Sjh 2018
     */
    private static class ChangeTimeIterator extends AbstractSearchableIterator{
        private final SearchableIterator input;
        private final TimePointL startTime;

        /**
         * update every entry key's startTime to `startTime`.
         * @param input
         * @param startTime
         */
        ChangeTimeIterator(SearchableIterator input, TimePointL startTime){
            this.input = input;
            this.startTime = startTime;
        }

        @Override
        protected InternalEntry computeNext() {
            if(input.hasNext()){
                InternalEntry entry = input.next();
                InternalKey key = entry.getKey();
                InternalKey newKey = new InternalKey(key.getId(), startTime, key.getValueType());
                return new InternalEntry(newKey, entry.getValue());
            }else{
                return endOfData();
            }
        }

        @Override
        public void seekToFirst() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean seekFloor(InternalKey targetKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "ChangeTimeIterator{" +
                    "input=" + input +
                    ", startTime=" + startTime +
                    '}';
        }
    }

    public static SearchableIterator setNewStart(SearchableIterator input, TimePointL time){
        return new ChangeTimeIterator(new TableLatestValueIterator(input), time);
    }
}
