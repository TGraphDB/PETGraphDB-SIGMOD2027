package org.act.temporalProperty.helper;

import org.act.temporalProperty.impl.*;
import org.act.temporalProperty.vo.EntityPropertyId;

/**
 * Created by song on 2018-01-24.
 * this class is used in range query for disk file to build a combined iterator of one entity.
 * the result iterator only contains entries for one entity, and its time is always inc when iterating
 * when adding sub iterators, should always add from earliest to latest (time is inc)
 * 注意：不同文件的时间虽然无overlap，但内部Key(entity id, pro Id, time)是有overlap的
 * should call seek() or seekToFirst() to initialize all sub-iterators.
 */
public class EPAppendIterator extends SameLevelMergeIterator {
    // each sub iterator's time should be inc (e.g. 0 is the earliest time)
    private EntityPropertyId id;

    public EPAppendIterator(EntityPropertyId id) {
        this.id = id;
    }

    public void append(SearchableIterator iterator) {
        if(isEP(iterator)){
            add(iterator);
        }else {
            add(new EPEntryIterator(id, iterator));
        }
    }

    private boolean isEP(SearchableIterator iterator) {
        return  (iterator instanceof EPEntryIterator) ||
                (iterator instanceof EPAppendIterator) ||
                (iterator instanceof EPMergeIterator) ||
                (iterator instanceof EPRangeQueryIterator.DebugIterator && isEP(((EPRangeQueryIterator.DebugIterator)iterator).in));
    }

    @Override
    public boolean seekFloor(InternalKey targetKey) {
        checkIfValidKey(targetKey);
        //直接返回下层SameLevelMergeIterator的结果。因为子Iterator都生成的是同一个entity的entry。（entity property都一样）
        return super.seekFloor( targetKey );
    }

    private void checkIfValidKey(InternalKey target) {
        if(!target.getId().equals(id)) throw new IllegalArgumentException("target should has same entity id and same property id");
    }

    @Override
    public String toString() {
        return "EPAppendIterator{" +
                "id=" + id +
                '}';
    }
}
