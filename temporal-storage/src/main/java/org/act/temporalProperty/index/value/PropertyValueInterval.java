package org.act.temporalProperty.index.value;

import org.act.temporalProperty.index.IndexValueType;
import org.act.temporalProperty.util.Slice;

/**
 * Created by song on 2018-01-20.
 */
public class PropertyValueInterval {
    private final int proId;
    private final Slice valueMin;
    private final Slice valueMax;
    private final IndexValueType type;

    public PropertyValueInterval(int proId, Slice valueMin, Slice valueMax, IndexValueType type) {
        this.proId = proId;
        this.type = type;
        this.valueMin = valueMin;
        this.valueMax = valueMax;
    }

    public int getProId() {
        return proId;
    }

    public IndexValueType getType() {
        return type;
    }

    public Slice getValueMin() {
        return valueMin;
    }

    public Slice getValueMax() {
        return valueMax;
    }

}
