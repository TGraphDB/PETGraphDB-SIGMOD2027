package edu.buaa.dataset;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import edu.buaa.common.benchmark.BenchmarkBuilder;
import edu.buaa.common.benchmark.TemporalGraphDataGenerator;
import edu.buaa.common.transaction.ImportStaticDataTx;
import edu.buaa.common.transaction.ImportTemporalDataTx;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Pair;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class EnergyWriteTxGenerator extends TemporalGraphDataGenerator {
    public static final String[] properties = "load_signal,solar_cosmo_p,solar_cosmo_u,solar_ecmwf_p,solar_ecmwf_u,wind_cosmo_p,wind_cosmo_u,wind_ecmwf_p,wind_ecmwf_u".split(",");
    private List<EnergyStatusCsvReader> propIters = new LinkedList<>();

    @Override
    public Iterator<ImportStaticDataTx> readNetwork(int size) throws IOException {
        return Collections.singleton(readNetwork()).iterator();
    }

    public ImportStaticDataTx readNetwork() throws IOException {
        PFieldList node = new PFieldList();
        try(BufferedReader reader = new BufferedReader(new FileReader(new File(dir, "network_nodes.csv")))){
            reader.readLine();
            String line;
            while((line = reader.readLine())!=null){
                String[] arr = line.split(",");
                node.add("u_sid", arr[0]);
                node.add("name", arr[1]);
                node.add("country", arr[2]);
                node.add("voltage",Integer.parseInt(arr[3]));
                node.add("latitude", Float.parseFloat(arr[4]));
                node.add("longitude", Float.parseFloat(arr[5]));
            }
        }
        PFieldList rel = new PFieldList();
        try(BufferedReader reader = new BufferedReader(new FileReader(new File(dir, "network_edges.csv")))){
            reader.readLine();//skip header
            String line;
            while((line = reader.readLine())!=null){
                String[] arr = line.split(",");
                rel.add("u_sid", arr[0]+":"+arr[1]);
                rel.add("r_from", arr[0]);
                rel.add("r_to", arr[1]);
                rel.add("x", Float.parseFloat(arr[2]));
                rel.add("y", Float.parseFloat(arr[3]));
                rel.add("r_limit", (int) Float.parseFloat(arr[4]));
                rel.add("r_length", Float.parseFloat(arr[5]));
            }
        }
        ImportStaticDataTx tx = new ImportStaticDataTx();
        tx.setNodes(node);
        tx.setRels(rel);
        return tx;
    }

    @Override
    public PeekingIterator<ImportTemporalDataTx> readNodeTemporal(String startDay, String endDay, int linePerTx) throws IOException {
        Repack it = new Repack(linePerTx, 160, Iterators.concat(new TxWrapper(parseTime(startDay), parseTime(endDay))));
        if(sectionEnable) return it;
        else return packUnique(it);
    }

    @Override
    public Iterator<ImportTemporalDataTx> readNodeTemporal(String beginTime, int linePerTx) throws IOException {
        return new Repack(linePerTx, 160, Iterators.concat(new TxWrapper(parseTime(beginTime), Integer.MAX_VALUE)));
    }

    @Override
    public PeekingIterator<ImportTemporalDataTx> readRelTemporal(String startDay, String endDay, int linePerTx) throws IOException {
        return Iterators.peekingIterator(Collections.emptyIterator());
    }

    @Override
    public PeekingIterator<ImportTemporalDataTx> readRelTemporal(String startDay, int linePerTx) throws IOException {
        return Iterators.peekingIterator(Collections.emptyIterator());
    }

    @Override
    public BenchmarkBuilder.BenchmarkRandomGen randomGen() {
        Set<Integer> noExist = new HashSet<>(Arrays.asList(759, 1154, 1193, 1225, 1243, 1245, 1246, 1247, 1248, 1249, 1252, 1255, 1256, 1263, 1264, 1276, 1340, 1399, 1501, 1504));
        Random rand = ThreadLocalRandom.current();
        return new BenchmarkBuilder.BenchmarkRandomGen() {
            @Override
            public boolean nodeOrEdge() {
                return true;
            }

            @Override
            public String entity(boolean isNode) {
                assert isNode;
                int i;
                do{ i = rand.nextInt(1514) + 1; }while(noExist.contains(i));
                return String.valueOf(i);
            }

            @Override
            public String prop(boolean isNode) {
                assert isNode;
                return properties[rand.nextInt(properties.length)];
            }

            @Override
            public List<PVal> valueIntervalStart(boolean isNode, String prop) {
                assert isNode;
                return Arrays.stream(new int[]{0, 100, 300}).mapToObj(PVal::f).collect(Collectors.toList());
            }

            @Override
            public Pair<PVal, PVal> valueRange(boolean isNode, String prop) {
                assert isNode;
                return Pair.of(PVal.f(900), PVal.f(1000));
            }

            @Override
            public int snapshotInterval(boolean isNode) {
                assert isNode;
                return 3600;
            }
        };
    }

    private static final DateTimeFormatter format1 = DateTimeFormatter.ofPattern("yyyyMMdd");//  20120101
    public int parseTime(String s) {
        LocalDate parsedDate;
        parsedDate = LocalDate.parse(s, format1);
        long timestamp = Date.from(parsedDate.atStartOfDay(ZoneOffset.ofHours(8)).toInstant()).getTime();
//            System.out.println(timestamp);
        return Math.toIntExact(timestamp / 1000);
    }

    public void close() throws IOException {
        for(EnergyStatusCsvReader r:propIters){
            r.close();
        }
    }

    private static class Repack extends AbstractIterator<ImportTemporalDataTx> implements PeekingIterator<ImportTemporalDataTx> {
        private final int txCnt;
        private final int sectionCount;
        private final Iterator<EnergyStatus> iterator;
        private final Map<Integer, LinkedList<EnergyStatus>> map = new HashMap<>();

        public Repack(int txCnt, int sectionCount, Iterator<EnergyStatus> iterator){
            this.txCnt = txCnt;
            this.sectionCount = sectionCount;
            this.iterator = iterator;
        }

        @Override
        protected ImportTemporalDataTx computeNext() {
            while(iterator.hasNext()){
                EnergyStatus status = iterator.next();
                int section = map2section(status.getNodeId());
                LinkedList<EnergyStatus> bucket = map.computeIfAbsent(section, integer -> new LinkedList<>());
                bucket.add(status);
                if(bucket.size()>=txCnt){
                    PFieldList prop = new PFieldList();
                    for(EnergyStatus s: bucket) {
                        prop.add("u_sid", Integer.toString(s.nodeId));
                        prop.add("t", s.time);
                        for(int j=0; j<properties.length; j++){
                            prop.add(properties[j], s.status.get(j));
                        }
                    }
                    map.remove(section);
                    ImportTemporalDataTx tx = new ImportTemporalDataTx();
                    tx.setData(prop);
                    tx.setSection(section);
                    tx.setNode(true);
                    return tx;
                }
            }
            PFieldList prop = new PFieldList();
            Iterator<Map.Entry<Integer, LinkedList<EnergyStatus>>> it = map.entrySet().iterator();
            if(it.hasNext()) {
                Map.Entry<Integer, LinkedList<EnergyStatus>> entry = it.next();
                it.remove();
                int section = entry.getKey();
                for(EnergyStatus status: entry.getValue()) {
                    prop.add("u_sid", Integer.toString(status.nodeId));
                    prop.add("t", status.time);
                    for (int j = 0; j < properties.length; j++) {
                        prop.add(properties[j], status.status.get(j));
                    }
                }
                ImportTemporalDataTx tx = new ImportTemporalDataTx();
                tx.setData(prop);
                tx.setSection(section);
                tx.setNode(true);
                return tx;
            }
            return endOfData();
        }

        private int map2section(int entityId){
            return entityId % sectionCount;
        }
    }

    private class TxWrapper extends AbstractIterator<Iterator<EnergyStatus>> {
        private final int timeMin, timeMax;
        public TxWrapper(int startDay, int endDay) throws IOException {
            timeMin = startDay;
            timeMax = endDay;
            propIters = new LinkedList<>();
            for(String p : properties){
                EnergyStatusCsvReader reader = new EnergyStatusCsvReader(new File(dir, p + ".csv"));
                floor(reader, timeMin);
                propIters.add(reader);
            }
        }

        private void floor(EnergyStatusCsvReader reader, int time) {
            while(reader.hasNext()){
                MultiEntityEnergyStatus content = reader.peek();
                if(content.getTimePoint()<time) {
                    reader.next();
                }else{
                    return;
                }
            }
        }

        @Override
        protected Iterator<EnergyStatus> computeNext() {
            Map<Integer, List<Float>> values = new HashMap<>();
            final int[] time = new int[]{-1};
            for(EnergyStatusCsvReader reader : propIters){
                if(!reader.hasNext()) return endOfData();
                MultiEntityEnergyStatus content = reader.next();
                if(content.getTimePoint()>=timeMax) return endOfData();
                if(time[0]==-1) time[0] = content.getTimePoint();
                else assert time[0]==content.getTimePoint():"expect "+time[0]+" but get "+content.getTimePoint()+" in "+reader.file;
                content.getStatus().forEach((nodeId, val) -> {
                    values.putIfAbsent(nodeId, new ArrayList<>());
                    List<Float> lst = values.get(nodeId);
                    lst.add(val);
                });
            }
            List<EnergyStatus> result = new ArrayList<>();
            values.forEach((nodeId, propVals) -> {
                EnergyStatus s = new EnergyStatus();
                s.setTime(time[0]+timeShiftInTp);
                s.setNodeId(nodeId);
                s.setStatus(propVals);
                result.add(s);
            });
            return result.iterator();
        }
    }

    private static class MultiEntityEnergyStatus {
        private int timePoint;
        private Map<Integer, Float> status; // nodeid, value

        public Map<Integer, Float> getStatus() {
            return status;
        }

        public void setStatus(Map<Integer, Float> status) {
            this.status = status;
        }

        public int getTimePoint() {
            return timePoint;
        }

        public void setTimePoint(int timePoint) {
            this.timePoint = timePoint;
        }
    }

    public static class EnergyStatus {
        private int nodeId;
        private int time;
        private List<Float> status;//

        public int getNodeId() {
            return nodeId;
        }

        public void setNodeId(int nodeId) {
            this.nodeId = nodeId;
        }

        public List<Float> getStatus() {
            return status;
        }

        public void setStatus(List<Float> status) {
            this.status = status;
        }

        public int getTime() {
            return time;
        }

        public void setTime(int time) {
            this.time = time;
        }

        @Override
        public String toString() {
            return "EnergyStatus{" +
                    "nodeId=" + nodeId +
                    ", time=" + time +
                    ", status=" + status +
                    '}';
        }
    }


    public static class EnergyStatusCsvReader extends AbstractIterator<MultiEntityEnergyStatus> implements Closeable {
        private final BufferedReader reader;
        private final Map<Integer, Integer> idColMap = new HashMap<>();
        public final File file;

        public EnergyStatusCsvReader(File file) throws IOException {
            this.file = file;
            reader = new BufferedReader(new FileReader(file));
            String header = reader.readLine();
            parseHeader(header);
        }

        @Override
        protected MultiEntityEnergyStatus computeNext() {
            try {
                String line = reader.readLine();
                if (line == null) {
                    return endOfData();
                } else {
                    return parseLine(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("fail to read traffic file.");
            }
        }

        private void parseHeader(String header) {
            String[] arr = header.split(",");
            for(int i=1; i<arr.length; i++){
                idColMap.put(i, Integer.parseInt(arr[i]));
            }
        }

        private MultiEntityEnergyStatus parseLine(String line) {
            HashMap<Integer, Float> r = new HashMap<>();
            String[] arr = line.split(",");
            for(int i=1; i<arr.length; i++){
                Integer id = idColMap.get(i);
                r.put(id, Float.parseFloat(arr[i]));
            }
            MultiEntityEnergyStatus e = new MultiEntityEnergyStatus();
            e.setStatus(r);
            e.setTimePoint(parseTime(arr[0]));
            return e;
        }

        private final DateTimeFormatter format2 = DateTimeFormatter.ofPattern("yyyy/M/d H:mm");//  2012/1/1 7:00 in solar_cosmo_p, solar_cosmo_u
        private final DateTimeFormatter format1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");//  2012-01-01 07:00:00 in others
        private int parseTime(String s) {
            LocalDateTime parsedDate;
            try {
                parsedDate = LocalDateTime.parse(s, format1);
            } catch (DateTimeParseException ignore) {
                parsedDate = LocalDateTime.parse(s, format2);
            }
            long timestamp = parsedDate.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
            return Math.toIntExact(timestamp / 1000);
        }

        public void close() throws IOException {
            reader.close();
        }

    }

    public Map<String, Object> stat(boolean isNode, String beginT, String endT) throws IOException {
        if(!isNode) return Collections.emptyMap();
        TxWrapper it = new TxWrapper(parseTime(beginT), parseTime(endT));
        long totalRecordCnt = 0;
        DataStatistic<Float> entity = new DataStatistic<>(properties);
        while(it.hasNext()){
            Iterator<EnergyStatus> tx = it.next();
            while(tx.hasNext()){
                EnergyStatus es = tx.next();
                totalRecordCnt++;

                long updateCntBefore = entity.count(String.valueOf(es.nodeId), es.time, totalRecordCnt,
                        es.getStatus().toArray(new Float[0]));
                if(updateCntBefore>0){
                    entity.vd.add((int) updateCntBefore);
                }
            }
        }
        System.out.println("entity\n"+entity.vd.toString(200));
        System.out.println(entity.mem);
        return Collections.emptyMap();
    }
}


//
//    ArrayList<String[]> load_signal = Helper.csvReader("PATH");//本地需求
//    ArrayList<String[]> solar_cosmo_p = Helper.csvReader("PATH");//太阳能在 COSMO 下 Proportional(比例) 产能布局下的信号
//    ArrayList<String[]> solar_cosmo_u = Helper.csvReader("PATH");//太阳能在 COSMO 下 Uniform(统一) 产能布局下的信号
//    ArrayList<String[]> solar_ecmwf_p = Helper.csvReader("PATH");//太阳能在 ECMWF 下 Proportional(比例) 产能布局下的信号
//    ArrayList<String[]> solar_ecmwf_u = Helper.csvReader("PATH");//太阳能在 ECMWF 下 Uniform(统一) 产能布局下的信号
//    ArrayList<String[]> wind_cosmo_p = Helper.csvReader("PATH");//风能在 COSMO 下 Proportional(比例) 产能布局下的信号
//    ArrayList<String[]> wind_cosmo_u = Helper.csvReader("PATH");//风能在 COSMO 下 Uniform(统一) 产能布局下的信号
//    ArrayList<String[]> wind_ecmwf_p = Helper.csvReader("PATH");//风能在 ECMWF 下 Proportional(比例) 产能布局下的信号
//    ArrayList<String[]> wind_ecmwf_u = Helper.csvReader("PATH");//风能在 ECMWF 下 Uniform(统一) 产能布局下的信号
//    ArrayList<String[]> nodeStaticDataList = Helper.csvReader("PATH");

