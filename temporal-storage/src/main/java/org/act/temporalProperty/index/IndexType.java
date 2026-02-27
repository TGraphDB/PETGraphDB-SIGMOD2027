package org.act.temporalProperty.index;

import com.google.common.base.Preconditions;
import org.act.temporalProperty.meta.ValueContentType;

/**
 * Created by song on 2018-01-18.
 */
public enum IndexType {
    SINGLE_VALUE(0), MULTI_VALUE(1),
    AGGR_DURATION(2), AGGR_MIN(3), AGGR_MAX(4), AGGR_MIN_MAX(5);

    int id;
    IndexType(int id){
        this.id = id;
    }

    public int getId(){return id;}

    public boolean isValueIndex(){
        return id<2;
    }

    public static IndexType decode(int i){
        Preconditions.checkArgument(0<=i && i<=5);
        switch (i){
            case 0: return SINGLE_VALUE;
            case 1: return MULTI_VALUE;
            case 2: return AGGR_DURATION;
            case 3: return AGGR_MIN;
            case 4: return AGGR_MAX;
            default:return AGGR_MIN_MAX;
        }
    }
}
