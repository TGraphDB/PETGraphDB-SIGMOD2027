package org.act.temporalProperty.util;

import org.act.temporalProperty.impl.*;
import org.act.temporalProperty.table.TwoLevelMergeIterator;
import org.act.temporalProperty.table.TableComparator;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;

/**
 * 测试基于优先队列的merge迭代器(MergingIterator)和基于代码判断的merge迭代器(TwoLevelMergeIterator)哪个更快
 * 目前看来后者更快。
 * Created by song on 2018-03-27.
 *
 * invalid because interface change to InternalKey.
 */
public class MergePerformanceComparison {
    private static Logger log = LoggerFactory.getLogger(MergePerformanceComparison.class);
    private String dbDir(){
        if(SystemUtils.IS_OS_WINDOWS){
            return "temporal.property.test";
        }else{
            return "/tmp/temporal.property.test";
        }
    }

//    @Test
//    public void compare(){
//        List<List<Integer>> data = generateData(7);
//        log.info("data generated.");
//
//        List<SeekingIterator<Slice,Slice>> iteratorList4MergingIterator = generateIters(data);
//        List<SeekingIterator<Slice,Slice>> myIters = generateIters(data);
//        MergingIterator mergingIter = new MergingIterator(iteratorList4MergingIterator, TableComparator.instance());
////        AppendIterator appendIterator = new AppendIterator();
////        appendIterator.append(myIters.get(5));
////        appendIterator.append(myIters.get(6));
////        appendIterator.append(myIters.get(3));
//        TwoLevelMergeIterator my0 = TwoLevelMergeIterator.merge(myIters.get(0), myIters.get(1));
//        TwoLevelMergeIterator my1 = TwoLevelMergeIterator.merge(myIters.get(2), myIters.get(3));
//        TwoLevelMergeIterator my2 = TwoLevelMergeIterator.merge(myIters.get(4), myIters.get(5));
//        TwoLevelMergeIterator my3 = TwoLevelMergeIterator.merge(my0, myIters.get(6));
//        TwoLevelMergeIterator my4 = TwoLevelMergeIterator.merge(my1, my2);
//        TwoLevelMergeIterator myIter = TwoLevelMergeIterator.merge(my3, my4);
//        myIter = TwoLevelMergeIterator.merge(myIter, myIters.get(4));
//        log.info("iterators assembled");
//
//        List<Entry<Slice,Slice>> rMerging = new ArrayList<>();
//        log.info("time {}", System.currentTimeMillis());
//        while(mergingIter.hasNext()){
//            rMerging.add(mergingIter.next());
//        }
//        log.info("time {}", System.currentTimeMillis());
//        log.info("merging iterator done");
//        log.info("time {}", System.currentTimeMillis());
//        List<InternalEntry> rMy = new ArrayList<>();
//        while(myIter.hasNext()){
//            rMy.add(myIter.next());
//        }
//        log.info("time {}", System.currentTimeMillis());
//        log.info("my iterator done");
//
//        validateResult(data, rMerging, rMy);
//    }

    private void validateResult(List<List<Integer>> data, List<Entry<Slice, Slice>> rMerging, List<InternalEntry> rMy) {
        Set<Integer> resultSet = new HashSet<>();
        for(List<Integer> l : data){
            resultSet.addAll(l);
        }
        log.info("merging size {}, my size {}", rMerging.size(), rMy.size());
        Set<Integer> rMergingSet = new HashSet<>();
//        for(int i=0; i<rMerging.size(); i++){
//            InternalKey keyMerge = new InternalKey(rMerging.get(i).getKey());
//            InternalKey keyMy = new InternalKey(rMy.get(i).getKey());
//            rMergingSet.add(keyMy.getStartTime());
//            if(keyMerge.getStartTime()!=keyMy.getStartTime()){
//                log.debug("time not equal! {} {}", keyMerge.getStartTime(), keyMy.getStartTime() );
//            }
//        }
        if(resultSet.size()!=rMergingSet.size() && resultSet.retainAll(rMergingSet)){
            throw new RuntimeException("result not equal!");
        }
    }

    private List<List<Integer>> generateData(int listCount) {
        List<List<Integer>> result = new ArrayList<>();
        for(int i=0; i<listCount; i++){
            result.add(randomIntList());
        }
        return result;
    }

//    private List<SeekingIterator<Slice, Slice>> generateIters(List<List<Integer>> data) {
//        List<SeekingIterator<Slice, Slice>> result = new ArrayList<>();
//        for(List<Integer> d : data){
//            MemTable m = new MemTable(TableComparator.instance());
//            Slice pEid = new Slice(12);
//            for(Integer time : d) {
//                InternalKey key = new InternalKey(pEid, time);
//                m.addToNow(key, new Slice(0));
//            }
//            result.add(m.iterator());
//        }
//        return result;
//    }

    private List<Integer> randomIntList(){
        List<Integer> result = new ArrayList<>();
        Random random = new Random(System.currentTimeMillis());
        for(int i=0; i<1000000; i++){
            result.add(random.nextInt(10000000));
        }
        result.sort(Integer::compareTo);
        return result;
    }
}
