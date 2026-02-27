//package org.act.temporalProperty.impl.index.singleval;
//
//import com.google.common.collect.Lists;
//import org.act.temporalProperty.TemporalPropertyStore;
//import org.act.temporalProperty.index.value.IndexQueryRegion;
//import org.act.temporalProperty.index.IndexValueType;
//import org.act.temporalProperty.index.value.PropertyValueInterval;
//import org.act.temporalProperty.index.value.rtree.IndexEntry;
//import org.act.temporalProperty.meta.ValueContentType;
//import org.act.temporalProperty.util.Slice;
//import org.act.temporalProperty.util.StoreBuilder;
//import org.junit.Test;
//
//import java.util.List;
//
///**
// * Created by song on 2018-03-30.
// */
//public class SmallDataTest {
//    private static String dbDir = "/tmp/temporal.property.test";
//    @Test
//    public void test() throws Throwable {
//        StoreBuilder stBuilder = new StoreBuilder(dbDir, true);
//        TemporalPropertyStore store = stBuilder.store();
//        store.createProperty(0, ValueContentType.INT);
//        StoreBuilder.setIntProperty(store, 10, 2, 0, 2);
//        store.createValueIndex(0,20, Lists.newArrayList(0));
//        List<IndexEntry> result = store.getEntries(packCondition(3,18, 0, 8));
//        for(IndexEntry entry: result){
//            System.out.println(entry);
//        }
//    }
//
//    private IndexQueryRegion packCondition(int timeMin, int timeMax, int valueMin, int valueMax){
//        IndexQueryRegion condition = new IndexQueryRegion(timeMin, timeMax);
//        Slice minValue = new Slice(4);
//        minValue.setInt(0, valueMin);
//        Slice maxValue = new Slice(4);
//        maxValue.setInt(0, valueMax);
//        condition.add(new PropertyValueInterval(0, minValue, maxValue, IndexValueType.INT));
//        return condition;
//    }
//
//}
