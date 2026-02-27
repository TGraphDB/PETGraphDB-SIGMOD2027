package edu.buaa.common.benchmark;

import com.alibaba.fastjson.JSON;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Pair;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static edu.buaa.common.transaction.AbstractTransaction.TxType.*;

/**
 * Generate an instance of benchmark (a iterator/list of transactions) from given arguments.
 */
public class BenchmarkBuilder {
    private static final String benchmarkFile = env("BENCHMARK_FULL_PATH");

    protected final String dataset = Helper.mustEnv("DATASET");
    protected final String dataSize = Helper.mustEnv("DATA_SIZE");
    private static final int BENCHMARK_SIZE = Integer.parseInt(Helper.mustEnv("QUERY_CNT"));
    private static final int WRITE_TX_SIZE = Integer.parseInt(Helper.envOrDefault("APPEND_TX_SIZE", "100"));
    private static final boolean IS_VALIDATE = "true".equalsIgnoreCase(System.getenv("NEED_RESULT"));
    private final String dataPath;
    private final String startDay;
    private final String endDay;

    protected final TemporalGraphDataGenerator dataGen;
    private final BenchmarkRandomGen dataRand;

    public BenchmarkBuilder() throws Exception {
        String tpRange = Helper.mustEnv(dataSize);
        startDay = tpRange.split("~")[0];
        endDay = tpRange.split("~")[1];
        dataPath = env("DIR_DATA");
        dataGen = txGenerator(dataset);
        dataRand = dataGen.randomGen();
    }

    private TemporalGraphDataGenerator txGenerator(String dataset) throws Exception{
        Class<?> cls = Class.forName(env("CLASS_DATA_GEN"));
        Object obj = cls.newInstance();
        TemporalGraphDataGenerator dg = (TemporalGraphDataGenerator) obj;
        return dg.init(new File(dataPath, dataset), TemporalGraphPropertySchema.load(dataset));
    }

    private static String env(String key){
        return Helper.mustEnv(key);
    }

    public static void main(String[] args) throws Exception {
        BenchmarkBuilder builder = new BenchmarkBuilder();
        BenchmarkWriter writer = new BenchmarkWriter(new File(benchmarkFile), false);

        writer.write(builder.readWriteMix(builder.distribution()));
        writer.close();
    }

    private List<Integer> distribution(){
        List<Integer> arr = JSON.parseArray("["+Helper.mustEnv("rq_distribution".toUpperCase())+"]", Integer.class);
        return arr.stream().map(i -> i * BENCHMARK_SIZE / 100).collect(Collectors.toList());
    }

    public interface BenchmarkRandomGen{
        boolean nodeOrEdge();
        String entity(boolean isNode);
        String prop(boolean isNode);
        List<PVal> valueIntervalStart(boolean isNode, String prop);
        Pair<PVal, PVal> valueRange(boolean isNode, String prop);
        int snapshotInterval(boolean isNode);
    }

    private Iterator<AbstractTransaction> readWriteMix(List<Integer> arr) throws IOException {
//        System.out.println(arr);
        int tBegin = dataGen.parseTime(startDay);
        int tFinish = dataGen.parseTime(endDay);
        // append, update, entity_history, snapshot, aggr_max, aggr_dur, entity_temporal_condition, reachable_area
        Stream<AbstractTransaction> txAppend = tx(tx_import_temporal_data, arr.get(0));

        Stream<AbstractTransaction> txs = Stream.of(
                tx(tx_update_temporal_data, arr.get(1)),
                tx(tx_query_entity_history, arr.get(2)),
                tx(tx_query_snapshot, arr.get(3)),
                tx(tx_query_snapshot_aggr_max, arr.get(4)),
                tx(tx_query_snapshot_aggr_duration, arr.get(5)),
                tx(tx_query_road_by_temporal_condition, arr.get(6)),
                tx(tx_query_reachable_area, dataset.equals("energy") ? 0 : arr.get(7))
                ).flatMap(tx->tx)
                .map(nodeOrRel())
                .map(entityId())
                .map(propName())
                .map(updateArgs(WRITE_TX_SIZE))
                .map(uniformStartTime(tBegin, tFinish, 5, 50))
                .map(uniformReachable(tBegin, tFinish))
                .map(uniformDurValIntStart())
                .map(uniformCondValStart());
        if(IS_VALIDATE){
            return txs.flatMap(appendValidate()).flatMap(updateValidate())
                    .map(uniformDurValIntStart()).iterator();
        }else {
            List<AbstractTransaction> lst = txs.collect(Collectors.toList());
            Collections.shuffle(lst);
            List<AbstractTransaction> appendList = txAppend.collect(Collectors.toList());
            LinkedList<Integer> queue = orderedRandomList(appendList.size(), lst.size());
            List<AbstractTransaction> result = new LinkedList<>();
            Iterator<AbstractTransaction> appendIter = appendList.iterator();
            for(int i=0;i<lst.size();i++){
                while(!queue.isEmpty() && queue.getFirst()==i && appendIter.hasNext()){
                    queue.pollFirst();
                    result.add(appendIter.next());
                }
                result.add(lst.get(i));
            }
            return result.iterator();
        }
    }

    private LinkedList<Integer> orderedRandomList(int size, int max) {
        Random rand = ThreadLocalRandom.current();
        LinkedList<Integer> result = new LinkedList<>();
        while(result.size()<size) result.add(rand.nextInt(max));
        Collections.sort(result);
        return result;
    }

    Stream<AbstractTransaction> tx(AbstractTransaction.TxType type, int cnt) throws IOException {
        System.out.println(type +" "+ cnt);
        if(cnt==0) return Stream.empty();
        else if(type==tx_import_temporal_data) {
            return StreamSupport.stream(appendTx(cnt), false);
        } else{
            return StreamSupport.stream(limit(create(type), cnt), false);
        }
    }

    public static Spliterator<AbstractTransaction> limit(Supplier<AbstractTransaction> supplier, int cnt) {
        return new Spliterators.AbstractSpliterator<AbstractTransaction>(cnt, Spliterator.SIZED) {
            private int i = 0;
            @Override
            public boolean tryAdvance(Consumer<? super AbstractTransaction> action) {
                action.accept(supplier.get());
                return ++i < cnt;
            }
        };
    }

    public Spliterator<AbstractTransaction> appendTx(int cnt) throws IOException {
        return new Spliterators.AbstractSpliterator<AbstractTransaction>(cnt, Spliterator.SIZED) {
            private int i = 0;
            final Iterator<ImportTemporalDataTx> nit = dataGen.readNodeTemporal(endDay, WRITE_TX_SIZE);
            final Iterator<ImportTemporalDataTx> eit = dataGen.readRelTemporal(endDay, WRITE_TX_SIZE);
            @Override
            public boolean tryAdvance(Consumer<? super AbstractTransaction> action) {
                ImportTemporalDataTx tx = null;
                if(nit.hasNext() && eit.hasNext()) {
                    tx = ThreadLocalRandom.current().nextBoolean() ? nit.next() : eit.next();
                }else if(nit.hasNext()){
                    tx = nit.next();
                }else if(eit.hasNext()){
                    tx = eit.next();
                }
                if(tx!=null) {
                    action.accept(tx);
                    return ++i < cnt;
                }else {
                    return false;
                }
            }
        };
    }

    public Supplier<AbstractTransaction> create(AbstractTransaction.TxType type){
        return () -> {
            switch (type){
                case tx_query_snapshot: return new SnapshotQueryTx();
                case tx_query_snapshot_aggr_max: return new SnapshotAggrMaxTx();
                case tx_query_snapshot_aggr_duration: return new SnapshotAggrDurationTx();
                case tx_query_road_by_temporal_condition: return new EntityTemporalConditionTx();
                case tx_query_entity_history: return new EntityHistoryTx();
                case tx_query_reachable_area: return new ReachableAreaQueryTx();
//                case tx_import_temporal_data: return iterator.;
                case tx_update_temporal_data: return new UpdateTemporalDataTx();
                default: throw new UnsupportedOperationException();
            }
        };
    }

    private Function<AbstractTransaction, AbstractTransaction> nodeOrRel() {
        return tx -> {
            boolean isNode = dataRand.nodeOrEdge();
            switch (tx.getTxType()){
                case tx_query_snapshot:
                    ((SnapshotQueryTx) tx).setNode(isNode);
                    break;
                case tx_query_snapshot_aggr_max:
                    ((SnapshotAggrMaxTx) tx).setNode(isNode);
                    break;
                case tx_query_snapshot_aggr_duration:
                    ((SnapshotAggrDurationTx) tx).setNode(isNode);
                    break;
                case tx_query_road_by_temporal_condition:
                    ((EntityTemporalConditionTx) tx).setNode(isNode);
                    break;
                case tx_query_entity_history:
                    ((EntityHistoryTx) tx).setNode(isNode);
                    break;
                case tx_update_temporal_data:
                    ((UpdateTemporalDataTx) tx).setNode(isNode);
                    break;
                case tx_import_temporal_data:
                    ((ImportTemporalDataTx) tx).setNode(isNode);
            }
            return tx;
        };
    }

    private boolean isNode(AbstractTransaction tx){
        boolean isNode = false;
        switch (tx.getTxType()){
            case tx_query_snapshot:
                isNode = ((SnapshotQueryTx) tx).isNode();
                break;
            case tx_query_snapshot_aggr_max:
                isNode = ((SnapshotAggrMaxTx) tx).isNode();
                break;
            case tx_query_snapshot_aggr_duration:
                isNode = ((SnapshotAggrDurationTx) tx).isNode();
                break;
            case tx_query_road_by_temporal_condition:
                isNode = ((EntityTemporalConditionTx) tx).isNode();
                break;
            case tx_query_entity_history:
                isNode = ((EntityHistoryTx) tx).isNode();
                break;
            case tx_update_temporal_data:
                isNode = ((UpdateTemporalDataTx) tx).isNode();
                break;
            case tx_import_temporal_data:
                isNode = ((ImportTemporalDataTx) tx).isNode();
        }
        return isNode;
    }

    private String randProp(boolean isNode){
        return dataRand.prop(isNode);
    }

    public Function<AbstractTransaction, AbstractTransaction> propName(){
        return tx -> {
            switch (tx.getTxType()){
                case tx_query_snapshot:
                    ((SnapshotQueryTx) tx).setPropertyName(randProp(isNode(tx)));
                    break;
                case tx_query_snapshot_aggr_max:
                    ((SnapshotAggrMaxTx) tx).setP(randProp(isNode(tx)));
                    break;
                case tx_query_snapshot_aggr_duration:
                    ((SnapshotAggrDurationTx) tx).setP(randProp(isNode(tx)));
                    break;
                case tx_query_road_by_temporal_condition:
                    ((EntityTemporalConditionTx) tx).setP(randProp(isNode(tx)));
                    break;
                case tx_query_entity_history:
                    ((EntityHistoryTx)tx).setProp(randProp(isNode(tx)));
                    break;
                case tx_query_reachable_area:
                    ReachableAreaQueryTx t = ((ReachableAreaQueryTx) tx);
                    if(dataset.equals("traffic")){
                        t.setProp("travel_time");
                    }else if(dataset.equals("syn")) {
                        t.setProp("rtpi1");
                    }
            }
            return tx;
        };
    }

    public Function<AbstractTransaction, AbstractTransaction> uniformStartTime(int timeBegin, int timeEnd, int len1, int len2){
        return tx -> {
            Random random = ThreadLocalRandom.current();
            float p = random.nextFloat();
            int length = random.nextBoolean() ? len1 : len2;
            Pair<Integer, Integer> ti = calcTimeInterval(timeBegin, timeEnd, p, length, dataRand.snapshotInterval(isNode(tx)));
            switch (tx.getTxType()){
                case tx_query_snapshot:
                    int time = (int) (timeBegin + (timeEnd-timeBegin)*p);
                    ((SnapshotQueryTx) tx).setTimestamp(time);
                    break;
                case tx_query_snapshot_aggr_max:
                    ((SnapshotAggrMaxTx) tx).setT0(ti.getKey());
                    ((SnapshotAggrMaxTx) tx).setT1(ti.getValue());
                    break;
                case tx_query_snapshot_aggr_duration:
                    ((SnapshotAggrDurationTx) tx).setT0(ti.getKey());
                    ((SnapshotAggrDurationTx) tx).setT1(ti.getValue());
                    break;
                case tx_query_road_by_temporal_condition:
                    ((EntityTemporalConditionTx) tx).setT0(ti.getKey());
                    ((EntityTemporalConditionTx) tx).setT1(ti.getValue());
                    break;
                case tx_update_temporal_data:
                    PFieldList data = ((UpdateTemporalDataTx) tx).getData();
                    List<Object> st = data.getData().get("st");
                    List<Object> et = data.getData().get("et");
                    for(int i=0; i<st.size(); i++) {
                        st.set(i, ti.getKey());
                        et.set(i, ti.getValue());
                    }
                    break;
                case tx_query_entity_history:
                    assert tx instanceof EntityHistoryTx;
                    ((EntityHistoryTx)tx).setBeginTime(ti.getKey());
                    ((EntityHistoryTx)tx).setEndTime(ti.getValue());
                    break;
                case tx_query_reachable_area:
                    ((ReachableAreaQueryTx)tx).setDepartureTime(ti.getKey());
                    ((ReachableAreaQueryTx)tx).setTravelTime(ti.getValue()-ti.getKey());
                    break;
            }
            return tx;
        };
    }
    
    private Function<AbstractTransaction, AbstractTransaction> uniformReachable(int tBegin, int tFinish) {
        return tx -> {
            if(tx.getTxType()==tx_query_reachable_area){
                Random random = ThreadLocalRandom.current();
                float p = random.nextFloat();
                int length = random.nextInt(10)+1;
                Pair<Integer, Integer> ti = calcTimeInterval(tBegin, tFinish, p, length, 120);
                ((ReachableAreaQueryTx)tx).setDepartureTime(ti.getKey());
                ((ReachableAreaQueryTx)tx).setTravelTime(ti.getValue()-ti.getKey());
            }
            return tx;
        };
    }

    private Pair<Integer, Integer> calcTimeInterval(int timeBegin, int timeEnd, float p, int length, int snapshotInterval) {
        int expectMinLen = (length + 1) * snapshotInterval;
        if(timeEnd - timeBegin > expectMinLen) {
            timeEnd -= expectMinLen;
        }else {
            return Pair.of(timeBegin, timeEnd);
        }
        int st = (int) (timeBegin + (timeEnd-timeBegin)*p);
        int et = st + length * snapshotInterval;
        return Pair.of(st, et);
    }

    public Function<AbstractTransaction, AbstractTransaction> uniformCondValStart(){
        return tx -> {
            if(tx.getTxType() == tx_query_road_by_temporal_condition){
                EntityTemporalConditionTx t = (EntityTemporalConditionTx) tx;
                Pair<PVal, PVal> p = dataRand.valueRange(t.isNode(), t.getP());
                t.setVMin(p.getKey());
                t.setVMax(p.getValue());
            }
            return tx;
        };
    }

    public Function<AbstractTransaction, AbstractTransaction> uniformDurValIntStart(){
        return tx -> {
            if(tx.getTxType() == AbstractTransaction.TxType.tx_query_snapshot_aggr_duration){
                SnapshotAggrDurationTx t = (SnapshotAggrDurationTx) tx;
                List<PVal> ints = dataRand.valueIntervalStart(t.isNode(), t.getP());
                t.setIntervalStarts(ints);
            }
            return tx;
        };
    }

    public Function<AbstractTransaction, Stream<AbstractTransaction>> appendValidate(){
        return tx -> {
            List<AbstractTransaction> list = new LinkedList<>();
            list.add(tx);
            if(tx.getTxType() == tx_import_temporal_data){
                ImportTemporalDataTx t = (ImportTemporalDataTx) tx;
                int interval = dataRand.snapshotInterval(t.isNode());
                PFieldList data = t.getData();
                int size = data.size();
                for(int i=0; i<size; i++) {
                    int time = data.get("t", i).i();
                    list.add(eHistory(t.isNode(), time - interval, time + interval));
                }
            }
            return list.stream();
        };
    }


    private EntityHistoryTx eHistory(boolean isNode, int st, int et) {
        return eHistory(randProp(isNode), dataRand.entity(isNode), st, et);
    }

    private EntityHistoryTx eHistory(String prop, String entity, int st, int et) {
        EntityHistoryTx tx = new EntityHistoryTx();
        tx.setProp(prop);
        tx.setBeginTime(st);
        tx.setEndTime(et);
        tx.setEntity(entity);
        return tx;
    }

    public Function<AbstractTransaction, Stream<AbstractTransaction>> updateValidate(){
        return tx -> {
            List<AbstractTransaction> list = new LinkedList<>();
            if(tx.getTxType() == tx_update_temporal_data){
                list.add(tx);
                UpdateTemporalDataTx t = (UpdateTemporalDataTx) tx;
                int interval = dataRand.snapshotInterval(t.isNode());
                PFieldList data = t.getData();
                int size = data.size();
                for(int i=0; i<size; i++) {
                    int begin = data.get("st", i).i();
                    int end = data.get("et", i).i();
                    list.add(eHistory(t.isNode(), begin - interval, end + interval));
                }
            }else{
                list.add(tx);
            }
            return list.stream();
        };
    }

    public Function<AbstractTransaction, AbstractTransaction> entityId(){
        return tx -> {
            if(tx.getTxType()==tx_query_entity_history){
                EntityHistoryTx t = (EntityHistoryTx) tx;
                t.setEntity(dataRand.entity(t.isNode()));
            }else if(tx instanceof ReachableAreaQueryTx){
                ReachableAreaQueryTx t = (ReachableAreaQueryTx) tx;
                t.setStartNode(dataRand.entity(true));
            }
            return tx;
        };
    }

    public Function<AbstractTransaction, AbstractTransaction> updateArgs(int writeTxSize){
        return tx -> {
            if(tx.getTxType() == tx_update_temporal_data){
                UpdateTemporalDataTx t = (UpdateTemporalDataTx) tx;
                PFieldList data = t.getData();
                String prop = dataRand.prop(t.isNode());
                for(int i=0; i<writeTxSize; i++) {
                    data.add("st", 0);
                    data.add("et", 0);
                    data.add("u_sid", dataRand.entity(t.isNode()));
                    PVal v = dataRand.valueRange(t.isNode(), prop).getKey();
                    data.add(prop, v.getVal());
                }
            }
            return tx;
        };
    }


}
