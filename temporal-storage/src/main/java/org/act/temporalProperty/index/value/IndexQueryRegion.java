package org.act.temporalProperty.index.value;

import org.act.temporalProperty.query.TimePointL;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by song on 2018-01-18.
 */
public class IndexQueryRegion {
    private TimePointL timeMin;
    private TimePointL timeMax;
    private List<PropertyValueInterval> pValues = new ArrayList<>();

    public IndexQueryRegion(TimePointL timeMin, TimePointL timeMax) {
        this.timeMin = timeMin;
        this.timeMax = timeMax;
    }

    public TimePointL getTimeMin() {
        return timeMin;
    }

    public TimePointL getTimeMax() {
        return timeMax;
    }

    public void add(PropertyValueInterval propertyValueInterval){
        this.pValues.add(propertyValueInterval);
    }

    public List<PropertyValueInterval> getPropertyValueIntervals() {
        return pValues;
    }
}
