package org.act.temporalProperty.table;

import org.act.temporalProperty.helper.AbstractSearchableIterator;
import org.act.temporalProperty.helper.DebugIterator;
import org.act.temporalProperty.impl.InternalEntry;
import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.impl.SearchableIterator;
import org.act.temporalProperty.impl.ValueType;
import org.act.temporalProperty.vo.EntityPropertyId;

/**
 * 将相邻Level的数据（如某文件及其Buffer）合并，并组成统一的Iterator。
 * 参考{@code TwoLevelIntervalMergeIterator}
 * rewrite by sjh at 2019-10-24
 */
public class TwoLevelMergeIterator extends AbstractSearchableIterator
{
    public final SearchableIterator latest;
    public final SearchableIterator old;
    private InternalEntry oldCurrent;

    public TwoLevelMergeIterator(SearchableIterator latest, SearchableIterator old)
    {
        this.latest = DebugIterator.wrap(latest);
        this.old = DebugIterator.wrap(old);
    }

    //要解决3个问题：1前后问题，2遮罩减除old问题（相同id），3unknown问题（相同id），4某个runout的问题
    @Override
    protected InternalEntry computeNext() { //  注意：不同id没有遮罩问题！
        if (latest.hasNext() && old.hasNext()){
            InternalEntry mem = latest.peek();
            InternalEntry disk = old.peek();
            InternalKey memKey = mem.getKey();
            InternalKey diskKey = disk.getKey();

            int r = diskKey.compareTo(memKey);
            if(r<0){ // disk < mem，无论id是否相同，均不存在遮罩问题，直接返回disk
                oldCurrent = disk;
                old.next();
                return disk;
            }else if(r==0){ // disk==mem，说明id必然相同
                if(memKey.getValueType()==ValueType.UNKNOWN ){
                    oldCurrent = disk;
                    latest.next();
                    old.next();
                    return disk;
                } else {
                    oldCurrent = disk;
                    latest.next();//==mem
                    delOld(memKey.getId());
                    return mem;
                }
            }else{ // oldCurrent < mem < disk，若id相同则存在遮罩问题
                if(memKey.getValueType()==ValueType.UNKNOWN ){
                    //无需delOld因为这个是unknown所以old里是需要被返回的，所以disk也不用next
                    if(oldCurrent!=null && oldCurrent.getKey().getId().equals(memKey.getId())){
                        InternalKey tmp = new InternalKey(memKey.getId(), memKey.getStartTime(), oldCurrent.getKey().getValueType());
                        oldCurrent = new InternalEntry(tmp, oldCurrent.getValue());
                        latest.next();//==mem
                        return oldCurrent;
                    }else{
                        oldCurrent = null;
                        latest.next();//==mem
                        return mem;
                    }
                } else { // 返回latest且要删掉其所遮罩的相同id的old项
                    latest.next();//必须先调latest的next再delOld
                    delOld(memKey.getId());
                    return mem;
                }
            }
        }else if (latest.hasNext()){ // diskIter run out
            InternalKey memKey = latest.peek().getKey();
            if(memKey.getValueType()==ValueType.UNKNOWN ){
                //此处逻辑同上
                if(oldCurrent!=null && oldCurrent.getKey().getId().equals(memKey.getId())){
                    InternalKey tmp = new InternalKey(memKey.getId(), memKey.getStartTime(), oldCurrent.getKey().getValueType());
                    oldCurrent = new InternalEntry(tmp, oldCurrent.getValue());
                    latest.next();
                    return oldCurrent;
                }else{
                    oldCurrent = null;
                    return latest.next();//==mem
                }
            } else {//oldCurrent保持原状，也不需要delOld因为old Run out了
                return latest.next();//==mem
            }
        } else if (old.hasNext()){ // memIter run out
            return old.next();
        } else{ // both ran out
            return endOfData();
        }
    }

    //从old中移除相同ID的项，直到old>=latest，同时更新oldCurrent.
    //若latest无后续项，则将old中所有id与参数相同的项移除。
    private void delOld(EntityPropertyId id) {
        if(latest.hasNext()){
            InternalKey until = latest.peek().getKey();
            while(old.hasNext()){
                InternalKey oldKey = old.peek().getKey();
                if(oldKey.getId().equals(id) && oldKey.compareTo(until)<0){
                    oldCurrent = old.next();
                }else{
                    return;
                }
            }
        }else{
            while(old.hasNext()) {
                InternalKey oldKey = old.peek().getKey();
                if(oldKey.getId().equals(id)) {
                    oldCurrent = old.next();
                }else{
                    return;
                }
            }
        }
    }

    @Override
    public void seekToFirst()
    {
        super.resetState();
        oldCurrent = null;
        this.latest.seekToFirst();
        this.old.seekToFirst();
    }

    @Override// this method does not implement the semantic defined in SearchableIterator.
    // after call this method, next() may produce more than one entry whose key smaller-or-equal than targetKey.
    public boolean seekFloor(InternalKey targetKey )
    {
        oldCurrent = null;
        this.latest.seekFloor( targetKey );
        this.old.seekFloor( targetKey );
        return super.seekFloor(targetKey);
    }

    @Override
    public String toString() {
        return "TwoLevelMergeIterator@"+hashCode()+"{" +
                "latest=" + latest +
                ", old=" + old +
                '}';
    }

    public static SearchableIterator merge(SearchableIterator latest, SearchableIterator old){
        return DebugIterator.wrap(new TwoLevelMergeIterator(latest, old));
    }
}
