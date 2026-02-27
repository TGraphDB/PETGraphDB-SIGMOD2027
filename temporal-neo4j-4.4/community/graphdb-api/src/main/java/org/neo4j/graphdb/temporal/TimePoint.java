package org.neo4j.graphdb.temporal;


import org.act.temporalProperty.query.TimePointL;
import org.neo4j.annotations.api.PublicApi;

@PublicApi
public class TimePoint extends TimePointL {
    public static final TimePoint NOW = new TimePoint(true){
        @Override public boolean isNow() { return true; }
        @Override public boolean isInit(){ return false; }
        @Override public TimePoint pre() { throw new UnsupportedOperationException("should not call pre on TimePoint.NOW"); }
        @Override public TimePoint next() { throw new UnsupportedOperationException("should not call next on TimePoint.NOW"); }
        @Override public String toString() { return "NOW"; }
    };
    /*public static final TimePoint INIT = new TimePoint(false){
        @Override public boolean isNow() { return false; }
        @Override public boolean isInit(){ return true; }
        @Override public TimePoint pre() { throw new UnsupportedOperationException("should not call pre on TimePoint.INIT"); }
        @Override public TimePoint next() { throw new UnsupportedOperationException("should not call next on TimePoint.INIT"); }
        @Override public String toString() { return "INIT"; }
    };*/

    public TimePoint(long time) {
        super(time);
    }

    private TimePoint(boolean isNow){
        super(isNow);
    }

}
