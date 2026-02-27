package edu.buaa.dataset;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import edu.buaa.common.benchmark.BenchmarkBuilder;
import edu.buaa.common.benchmark.TemporalGraphDataGenerator;
import edu.buaa.common.transaction.ImportStaticDataTx;
import edu.buaa.common.transaction.ImportTemporalDataTx;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import com.google.common.collect.AbstractIterator;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.dataset.traffic.CrossNode;
import edu.buaa.dataset.traffic.RoadRel;
import edu.buaa.dataset.traffic.StatusUpdate;
import edu.buaa.dataset.traffic.TrafficTemporalPropertyGraph;
import edu.buaa.dataset.traffic.TrafficMultiFileReader;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Pair;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class TrafficWriteTxGenerator extends TemporalGraphDataGenerator {

    @Override
    public Iterator<ImportStaticDataTx> readNetwork(int size) throws IOException {
        return Collections.singleton(txImportStatic()).iterator();
    }

    @Override
    public PeekingIterator<ImportTemporalDataTx> readNodeTemporal(String startDay, String endDay, int linePerTx) throws IOException {
        return Iterators.peekingIterator(Collections.emptyIterator());
    }

    @Override
    public PeekingIterator<ImportTemporalDataTx> readNodeTemporal(String startDay, int linePerTx) throws IOException {
        return Iterators.peekingIterator(Collections.emptyIterator());
    }

    @Override
    public PeekingIterator<ImportTemporalDataTx> readRelTemporal(String startDay, String endDay, int linePerTx) throws IOException {
        return temporalTx(startDay, endDay, linePerTx);
    }

    @Override
    public PeekingIterator<ImportTemporalDataTx> readRelTemporal(String startDay, int linePerTx) throws IOException {
        return temporalTx(startDay, "1201", linePerTx);
    }

    @Override
    public int parseTime(String time) {
        return monthDayStr2Time(time);
    }

    @Override
    public BenchmarkBuilder.BenchmarkRandomGen randomGen() throws IOException {
        Random rand = ThreadLocalRandom.current();
        txImportStatic();
        ArrayList<String> roadIds = new ArrayList<>(roadSectionMap.keySet());
        ArrayList<String> crossIds = new ArrayList<>(crossIdMap.values());
        return new BenchmarkBuilder.BenchmarkRandomGen() {
            @Override
            public boolean nodeOrEdge() {
                return false;
            }

            @Override
            public String entity(boolean isNode) {
                return isNode? crossIds.get(rand.nextInt(crossIds.size())) : roadIds.get(rand.nextInt(roadIds.size()));
            }

            @Override
            public String prop(boolean isNode) {
                assert !isNode;
                String[] props = Arrays.asList("travel_time", "full_status", "segment_cnt").toArray(new String[0]);
                return props[rand.nextInt(3)];
            }

            @Override
            public List<PVal> valueIntervalStart(boolean isNode, String prop) {
                assert !isNode;
                if("travel_time".equals(prop)) return pv(0, 100, 200);
                if("full_status".equals(prop)) return pv(0,1,2);
                if("segment_cnt".equals(prop)) return pv(0,2,4);
                throw new IllegalStateException(prop);
            }

            private List<PVal> pv(int... s) {
                return Arrays.stream(s).mapToObj(PVal::i).collect(Collectors.toList());
            }

            @Override
            public Pair<PVal, PVal> valueRange(boolean isNode, String prop) {
                assert !isNode;
                if("travel_time".equals(prop)) return p(100, 200);
                if("full_status".equals(prop)) return p(1,2);
                if("segment_cnt".equals(prop)) return p(3,5);
                throw new IllegalStateException(prop);
            }

            @Override
            public int snapshotInterval(boolean isNode) {
                assert !isNode;
                return 300;
            }

            private Pair<PVal, PVal> p(int min, int max) {
                return Pair.of(PVal.i(min), PVal.i(max));
            }
        };
    }


    private final Map<String, Integer> roadSectionMap = new HashMap<>();
    private final Map<CrossNode, String> crossIdMap = new HashMap<>();

    public ImportStaticDataTx txImportStatic() throws IOException {
        TrafficTemporalPropertyGraph tgraph = new TrafficTemporalPropertyGraph();
        tgraph.importTopology(new File(dir, "road_topology.csv.gz"));
        PFieldList node = new PFieldList();
        long crossId = 0;
        for(CrossNode cross : tgraph.getAllCross()){
            String u_sid = Long.toString(crossId);
            node.add("u_sid", u_sid);
            node.add("name", cross.name.replace(',',' '));
            crossIdMap.put(cross, u_sid);
            crossId++;
        }
        PFieldList rel = new PFieldList();
        for(RoadRel r: tgraph.getAllRoads()){
            rel.add("u_sid", r.id);
            rel.add("r_from", crossIdMap.get(tgraph.getRoadEndCross(r)));
            rel.add("r_to", crossIdMap.get(tgraph.getRoadStartCross(r)));
            rel.add("angle", r.angle);
            rel.add("r_type", r.getType());
            rel.add("r_length", r.length);
            roadSectionMap.put(r.id, roadSectionMap.size());
        }
        ImportStaticDataTx tx = new ImportStaticDataTx();
        tx.setNodes(node);
        tx.setRels(rel);
        return tx;
    }

    public PeekingIterator<ImportTemporalDataTx> temporalTx(String startDay, String endDay, int linePerTx) throws IOException {
        if(crossIdMap.isEmpty() || roadSectionMap.isEmpty()) txImportStatic();
        List<File> temporalDataFiles = trafficFileList(dir.getAbsolutePath(), startDay, endDay);
//        System.out.println(temporalDataFiles);
        if(sectionEnable) {
            return new TemporalPropertyAppendTxGenerator(linePerTx, temporalDataFiles);
        }else{
            return new SimpleAppendGenerator(linePerTx, temporalDataFiles);
        }
    }

    public class SimpleAppendGenerator extends AbstractIterator<ImportTemporalDataTx> implements AutoCloseable, PeekingIterator<ImportTemporalDataTx> {
        final int linePerTx;
        private final TrafficMultiFileReader data;
        private PFieldList tp = new PFieldList();
        public SimpleAppendGenerator(int linePerTx, List<File> files)
        {
            this.linePerTx = linePerTx;
            this.data = new TrafficMultiFileReader(files);
        }
        @Override
        protected ImportTemporalDataTx computeNext()
        {
            while(data.hasNext()) {
                StatusUpdate s = data.next();
                Integer sec = roadSectionMap.get(s.getRoadId());
                assert sec!=null;
                tp.add("u_sid", s.getRoadId());
                tp.add("t", s.getTime()+timeShiftInTp);
                tp.add("travel_time", s.getTravelTime());
                tp.add("full_status", s.getJamStatus());
                tp.add("segment_cnt", s.getSegmentCount());
                if(tp.size()>=linePerTx) {
                    ImportTemporalDataTx tx = new ImportTemporalDataTx(tp, false);
                    tp = new PFieldList();
                    return tx;
                }
            }
            if(tp.size()>0) {
                ImportTemporalDataTx tx = new ImportTemporalDataTx(tp, false);
                tp = new PFieldList();
                return tx;
            }
            return endOfData();
        }

        @Override
        public void close(){
            data.close();
        }
    }

    //为何要实现为iterator？因为事务之间是有顺序的
    //这个没有用带缓存的文件读取器（TrafficDataReader），可能会偶尔卡一下。
    public class TemporalPropertyAppendTxGenerator extends AbstractIterator<ImportTemporalDataTx> implements AutoCloseable, PeekingIterator<ImportTemporalDataTx> {
        final int linePerTx;
        private final TrafficMultiFileReader data;
        private final BucketSectionMap map = new BucketSectionMap();
        public TemporalPropertyAppendTxGenerator(int linePerTx, List<File> files)
        {
            this.linePerTx = linePerTx;
            this.data = new TrafficMultiFileReader(files);
        }
        @Override
        protected ImportTemporalDataTx computeNext()
        {
            while(data.hasNext()) {
                StatusUpdate s = data.next();
                Integer sec = roadSectionMap.get(s.getRoadId());
                assert sec!=null;
                PFieldList tp = map.getBySection(sec);
                tp.add("u_sid", s.getRoadId());
                tp.add("t", s.getTime()+timeShiftInTp);
                tp.add("travel_time", s.getTravelTime());
                tp.add("full_status", s.getJamStatus());
                tp.add("segment_cnt", s.getSegmentCount());
                if(tp.size()>linePerTx) {
                    ImportTemporalDataTx tx = new ImportTemporalDataTx();
                    tx.setData(tp);
                    tx.setSection(sec);
                    map.rm(sec);
                    return tx;
                }
            }
            LinkedList<Map.Entry<Integer, PFieldList>> remains = map.getRemaining();
            if(!remains.isEmpty()){
                Map.Entry<Integer, PFieldList> entry = remains.pollFirst();
                ImportTemporalDataTx tx = new ImportTemporalDataTx();
                tx.setData(entry.getValue());
                tx.setSection(entry.getKey());
                return tx;
            }else {
                return endOfData();
            }
        }

        @Override
        public void close(){
            data.close();
        }
    }

    public static List<File> trafficFileList(String dir, String fileStart, String fileEnd) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(monthDayStr2Time(fileStart)*1000L);
        long endT = monthDayStr2Time(fileEnd)*1000L;
        File folder = new File(dir);
        if (!folder.exists() && !folder.mkdirs()) throw new RuntimeException("can not create dir.");
        List<String> files = new ArrayList<>();
        while(c.getTimeInMillis() < endT) {
            int month = c.get(Calendar.MONTH) + 1;
            int day = c.get(Calendar.DAY_OF_MONTH);
            if(!(month==8 && (day==1 || day==2))) {
                files.add(String.format("%02d%02d.csv.gz", month, day));
            }
            c.add(Calendar.HOUR, 24);
        }
        return files.stream().map(s -> new File(folder, s)).collect(Collectors.toList());
    }

    public static int monthDayStr2Time(String monthDayStr){
        Calendar c = Calendar.getInstance();
        int month = Integer.parseInt(monthDayStr.substring(0, 2))-1;
        int day = Integer.parseInt(monthDayStr.substring(2,4));
        c.set(2010, month, day, 0, 0 );
        return (int) (c.getTimeInMillis() / 1000L);
    }

    public Map<String, Object> stat(boolean isNode, String beginT, String endT) throws IOException {
        if(isNode) return Collections.emptyMap();
        List<File> temporalDataFiles = trafficFileList(dir.getAbsolutePath(), beginT, endT);
        long totalRecordCnt = 0;
        DataStatistic<Integer> entity = new DataStatistic<>("seg","jam","travel");
        for(File f: temporalDataFiles){
            try(BufferedReader reader = Helper.gzipReader(f)){
                reader.readLine();
                String line=null;
                while((line=reader.readLine())!=null){
                    StatusUpdate s = new StatusUpdate(line);
                    totalRecordCnt++;

                    long updateCntBefore = entity.count(s.getRoadId(), s.getTime(), totalRecordCnt,
                            s.getSegmentCount(), s.getJamStatus(), s.getTravelTime());
                    if(updateCntBefore>0){
                        entity.vd.add((int) updateCntBefore);
                    }
                }
            }
        }
        System.out.println("entity\n"+entity.vd.toString(200));
        System.out.println(entity.mem);
        return ImmutableMap.of("totalEntityCnt", totalRecordCnt);
    }
}
