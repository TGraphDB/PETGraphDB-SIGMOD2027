package edu.buaa.dataset;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import edu.buaa.common.benchmark.BenchmarkBuilder;
import edu.buaa.common.benchmark.TemporalGraphDataGenerator;
import edu.buaa.common.transaction.ImportStaticDataTx;
import edu.buaa.common.transaction.ImportTemporalDataTx;
import edu.buaa.common.transaction.UpdateTemporalDataTx;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.common.utils.SynGenerateSchema;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Pair;
import edu.buaa.utils.TPDataFileFormatConvertor;

import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipException;

public class SynWriteTxGenerator extends TemporalGraphDataGenerator {

    public Iterator<ImportStaticDataTx> readNetwork(int size) throws IOException {
        return Iterators.concat(
                new NodeTxWrapper(new File(dir, "vertex.csv"), size),
                new EdgeTxWrapper(new File(dir, "edge.csv"), size));
    }

    private static class NodeTxWrapper extends AbstractIterator<ImportStaticDataTx> implements PeekingIterator<ImportStaticDataTx>, AutoCloseable {
        private final BufferedReader reader;
        private final int size;

        NodeTxWrapper(File nodeFile, int size) throws IOException {
            this.size = size;
            reader = Helper.read(nodeFile);
            reader.readLine();
        }

        @Override
        protected ImportStaticDataTx computeNext() {
            ImportStaticDataTx result = new ImportStaticDataTx();
            PFieldList nodes = new PFieldList();
            String line = null;
            try {
                int cnt = 0;
                for (int i = 0; i < size; i++,cnt++) {
                    line = reader.readLine();
                    if(line==null) break;
                    String[] arr = line.split(",");
                    nodes.add("u_sid", PVal.s(arr[0]));
                }
                if(line==null && cnt==0) return endOfData();
            }catch (IOException e){
                throw new IllegalStateException(e);
            }
            result.setNodes(nodes);
            result.setRels(new PFieldList());
            return result;
        }

        @Override
        public void close() throws Exception {
            reader.close();
        }
    }

    private static class EdgeTxWrapper extends AbstractIterator<ImportStaticDataTx> implements PeekingIterator<ImportStaticDataTx>, AutoCloseable {
        private final BufferedReader reader;
        private final int size;

        EdgeTxWrapper(File edgeFile, int size) throws IOException {
            this.size = size;
            reader = Helper.read(edgeFile);
            reader.readLine();
        }

        @Override
        protected ImportStaticDataTx computeNext() {
            ImportStaticDataTx result = new ImportStaticDataTx();
            PFieldList rel = new PFieldList();
            String line = null;
            try {
                int cnt = 0;
                for (int i = 0; i < size; i++,cnt++) {
                    line = reader.readLine();
                    if(line==null) break;
                    String[] arr = line.split(",");
                    rel.add("u_sid", PVal.s(arr[0]));
                    rel.add("r_from", PVal.s(arr[1]));
                    rel.add("r_to", PVal.s(arr[2]));
                }
                if(line==null && cnt==0) return endOfData();
            }catch (IOException e){
                throw new IllegalStateException(e);
            }
            result.setRels(rel);
            result.setNodes(new PFieldList());
            return result;
        }

        @Override
        public void close() throws Exception {
            reader.close();
        }
    }

    public PeekingIterator<ImportTemporalDataTx> readNodeTemporal(String startTime, String endTime, int linePerTx) throws IOException {
        if(sectionEnable){
            return new TxWrapper(new File(dir, "vertex_tp.csv"), parseTime(startTime), parseTime(endTime), linePerTx, schema);
        }else{
            return new SimpleReader(new File(dir, "vertex_tp.csv"), parseTime(startTime), parseTime(endTime), linePerTx, schema);
        }
    }

    public PeekingIterator<ImportTemporalDataTx> readNodeTemporal(String startTime, int linePerTx) throws IOException {
        if(sectionEnable) {
            return new TxWrapper(new File(dir, "vertex_tp.csv"), parseTime(startTime), Integer.MAX_VALUE, linePerTx, schema);
        }else{
            return new SimpleReader(new File(dir, "vertex_tp.csv"), parseTime(startTime), Integer.MAX_VALUE, linePerTx, schema);
        }
    }

    public PeekingIterator<ImportTemporalDataTx> readRelTemporal(String startTime, String endTime, int linePerTx) throws IOException {
        if(sectionEnable) {
            return new TxWrapper(new File(dir, "edge_tp.csv"), parseTime(startTime), parseTime(endTime), linePerTx, schema);
        }else{
            return new SimpleReader(new File(dir, "edge_tp.csv"), parseTime(startTime), parseTime(endTime), linePerTx, schema);
        }
    }

    public PeekingIterator<ImportTemporalDataTx> readRelTemporal(String startTime, int linePerTx) throws IOException {
        if(sectionEnable) {
            return new TxWrapper(new File(dir, "edge_tp.csv"), parseTime(startTime), Integer.MAX_VALUE, linePerTx, schema);
        }else{
            return new SimpleReader(new File(dir, "edge_tp.csv"), parseTime(startTime), Integer.MAX_VALUE, linePerTx, schema);
        }
    }

//    private static final DateTimeFormatter format1 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");//  20120101
//    public int parseTime(String s) {
//        LocalDateTime parsedDate;
//        parsedDate = LocalDateTime.parse(s.substring(0, 19), format1);
//        long timestamp = Date.from(parsedDate.toInstant(ZoneOffset.ofHours(0))).getTime();
//        System.out.println(timestamp/1000);
//        return Math.toIntExact(timestamp / 1000);
//    }

    public int parseTime(String s) {
        return Integer.parseInt(s.replace("_", ""));
    }

    private class SimpleReader extends AbstractIterator<ImportTemporalDataTx> implements PeekingIterator<ImportTemporalDataTx>, AutoCloseable {
        private final BufferedReader reader;
        private final TemporalGraphPropertySchema schema;
        private final int startT;
        private final int endT;
        public final File file;
        private final boolean isNode;
        private final String[] header;
        private final int lineCnt;
        private PFieldList data = new PFieldList();

        public SimpleReader(File file, int startT, int endT, int lineCnt, TemporalGraphPropertySchema schema) throws IOException {
            this.file = file;
            this.startT = startT;
            this.endT = endT;
            this.lineCnt = lineCnt;
            this.schema = schema;
            this.isNode = file.getName().toLowerCase().contains("vertex");
            this.reader = Helper.read(file);
            this.header = this.reader.readLine().split(",");
        }

        @Override
        protected ImportTemporalDataTx computeNext() {
            try {
                String line;
                while((line = reader.readLine())!=null){
                    String[] arr = line.split(",");
                    int time = Integer.parseInt(arr[0]);
                    if(time<startT) continue;
                    if(time>=endT) break;
                    String id = arr[1];
                    data.add("u_sid", PVal.s(id));
                    data.add("t", PVal.i(time+timeShiftInTp));
                    for(int i=2; i<arr.length; i++){
                        String propName = header[i];
                        PVal.Type type = schema.getType(isNode, false, propName);
                        if(type== PVal.Type.INT) data.add(propName, Integer.parseInt(arr[i]));
                        else if(type== PVal.Type.FLOAT) data.add(propName, Float.parseFloat(arr[i]));
                        else throw new IllegalStateException("unexpected type: "+type);
                    }
                    if(data.size()>=lineCnt) return buildTx();
                }
                if(data.size()>0) return buildTx();
                return endOfData();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("fail to read syn file.");
            }
        }

        private ImportTemporalDataTx buildTx() {
            ImportTemporalDataTx tx = new ImportTemporalDataTx(data, isNode);
            this.data = new PFieldList();
            return tx;
        }

        public void close() throws IOException {
            reader.close();
        }
    }

    private class TxWrapper extends AbstractIterator<ImportTemporalDataTx> implements PeekingIterator<ImportTemporalDataTx>, AutoCloseable {
        private final BufferedReader reader;
        private final TemporalGraphPropertySchema schema;
        private final int startT;
        private final int endT;
        private final Map<Integer, PFieldList> bucket = new HashMap<>();
        public final File file;
        private final boolean isNode;
        private final String[] header;
        private int lineCnt;
        private final int rawLineCnt;
        private final Random random = ThreadLocalRandom.current();

        public TxWrapper(File file, int startT, int endT, int lineCnt, TemporalGraphPropertySchema schema) throws IOException {
            this.file = file;
            this.startT = startT;
            this.endT = endT;
            this.lineCnt = lineCnt;
            this.schema = schema;
            this.isNode = file.getName().toLowerCase().contains("vertex");
            this.rawLineCnt = lineCnt;
            this.reader = Helper.read(file);
            this.header = this.reader.readLine().split(",");
//            this.random.setSeed(88763);
        }

        @Override
        protected ImportTemporalDataTx computeNext() {
            try {
                String line;
                PFieldList data;
                while((line = reader.readLine())!=null){
                    String[] arr = line.split(",");
                    int time = Integer.parseInt(arr[0]);
                    if(time<startT) continue;
                    String id = arr[1];
                    int section = id2section(id);
                    data = bucket.computeIfAbsent(section, s -> new PFieldList());
                    if(time>=endT) return buildTx(-1);
                    data.add("u_sid", PVal.s(id));
                    data.add("t", PVal.i(time+timeShiftInTp));
                    for(int i=2; i<arr.length; i++){
                        String propName = header[i];
                        PVal.Type type = schema.getType(isNode, false, propName);
                        if(type== PVal.Type.INT) data.add(propName, Integer.parseInt(arr[i]));
                        else if(type== PVal.Type.FLOAT) data.add(propName, Float.parseFloat(arr[i]));
                        else throw new IllegalStateException("unexpected type: "+type);
                    }
                    if(data.size()>=lineCnt) return buildTx(section);
                }
                return buildTx(-1);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("fail to read syn file.");
            }
        }

        private int id2section(String id){
            return Integer.parseInt(id) % 128;
        }

        private ImportTemporalDataTx buildTx(int section) {
            if(section==-1){
                for(Iterator<Map.Entry<Integer, PFieldList>> it = bucket.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Integer, PFieldList> entry = it.next();
                    PFieldList pFieldList = entry.getValue();
                    if (pFieldList.size() > 0){
                        it.remove();
                        lineCnt = updateLineCnt();
                        return new ImportTemporalDataTx(pFieldList, isNode);
                    }
                }
                return endOfData();
            }else {
                PFieldList data = bucket.get(section);
                PFieldList firstK = data.head(lineCnt);
                lineCnt = updateLineCnt();
                return new ImportTemporalDataTx(firstK, isNode);
            }
        }

        private int updateLineCnt() {
            if(rawLineCnt>0) {
                return rawLineCnt;
            }else if(rawLineCnt<0){
                return random.nextInt(-rawLineCnt);
            }else throw new IllegalArgumentException("must not be 0");
        }

        public void close() throws IOException {
            reader.close();
        }
    }

    @Override
    public void writeTimeInterval(LinkedList<String> proName, PeekingIterator<ImportTemporalDataTx> it, BufferedWriter writer, boolean isNode, boolean isTimestamp, Map<String, Long> idMap) throws IOException {
        proName.addFirst("et");
        proName.addFirst("st");
        writer.write(String.join(",", proName));
        writer.write('\n');
        TPDataFileFormatConvertor.P2IConvAsync conv = new TPDataFileFormatConvertor.P2IConvAsync(it, Integer.MAX_VALUE, 1000, isNode, 30);
        conv.start();
        UpdateTemporalDataTx tx;
        try {
            do {
                tx = conv.next(10);
                if (tx != null) {
                    writeTpLine(tx.getData(), proName, writer, isTimestamp, idMap);
                }
            } while (!conv.done() || tx != null);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
//    private void toTimeInterval(File input, File output, int startT, int endT, boolean tms, Map<String, Long> idMap) throws Exception{
//        try(BufferedWriter writer = new BufferedWriter(new FileWriter(output));
//            TPDataFileFormatConvertor.TimeIntervalConv transF = new TPDataFileFormatConvertor.TimeIntervalConv(input, startT, endT, tms, idMap)) {
//            writer.write(transF.getHeaderLine());
//            writer.write('\n');
//            while(transF.hasNext()){
//                writer.write(transF.next());
//                writer.write('\n');
//            }
//            writer.flush();
//        }
//    }
//
//    private void toTimePoint(File input, File output, int startT, int endT, boolean tms, Map<String, Long> idMap) throws Exception{
//        try(BufferedWriter writer = new BufferedWriter(new FileWriter(output));
//            TPDataFileFormatConvertor.TimePointConv transF = new TPDataFileFormatConvertor.TimePointConv(input, startT, endT, tms, idMap)) {
//            writer.write(transF.getHeaderLine());
//            writer.write('\n');
//            while(transF.hasNext()){
//                writer.write(transF.next());
//                writer.write('\n');
//            }
//            writer.flush();
//        }
//    }

//    private final Map<String, Long> nIdMap = new HashMap<>();
//    private final Map<String, Long> rIdMap = new HashMap<>();
//    @Override
//    public File prepareStaticCSV(boolean isNode) throws IOException {
//        String fName = isNode ? "vertex" : "edge";
//        Map<String, Long> idMap = isNode ? nIdMap : rIdMap;
//        File input = new File(dir, fName+".csv.gz");
//        File output = new File(dir, fName+"_export.csv");
//        try(BufferedWriter writer = new BufferedWriter(new FileWriter(output));
//            BufferedReader reader = Helper.gzipReader(input)){
//            reader.readLine();
//            writer.write("id,u_sid");
//            if(!isNode) writer.write(",r_from,r_to");
//            writer.write('\n');
//            String line;
//            while((line=reader.readLine())!=null){
//                String[] arr = line.split(",");
//                long id = nextId(idMap, arr[0]);
//                writer.write(String.valueOf(id));
//                writer.write(',');
//                writer.write(line);
//                writer.write('\n');
//            }
//        }
//        return output;
//    }
//
//    private long nextId(Map<String, Long> idMap, String u_sid){
//        Long id = idMap.get("NEXT_ID");
//        if(id==null){
//            idMap.put("NEXT_ID", 2L);
//            id=1L;
//        }else{
//            idMap.put("NEXT_ID", id+1);
//        }
//        idMap.put(u_sid, id);
//        return id;
//    }
//
//    @Override
//    public File prepareTPCSV(String beginT, String endT, boolean isNode, boolean isTimeInterval, boolean isTimestamp) throws Exception {
//        File input = new File(dir, (isNode?"vertex":"edge")+"_temporal_data.csv.gz");
//        int st = parseTime(beginT);
//        int et = parseTime(endT);
//        Map<String, Long> idMap = isNode ? nIdMap : rIdMap;
//        File output = new File(dir, (isNode?"vertex":"edge")+"_temporal_data."+beginT+"_"+endT+".csv");
//        if(isTimeInterval){
//            toTimeInterval(input, output, st, et, isTimestamp, idMap);
//        }else{
//            toTimePoint(input, output, st, et, isTimestamp, idMap);
//        }
//        return output;
//    }

    public Map<String, Object> stat(boolean isNode, String beginT, String endT) throws IOException {
        TxWrapper it = new TxWrapper(new File(dir, isNode?"vertex_tp.csv":"edge_tp.csv"), parseTime(beginT), parseTime(endT), 1, schema);
        Set<String> set = isNode ? schema.nodeTemporal.keySet() : schema.relTemporal.keySet();
        SynGenerateSchema sg = SynGenerateSchema.load("syn1");
        int step = isNode? sg.getNode().getStep() : sg.getRel().getStep();
        DataStatistic<PVal> stat = new DataStatistic<>(set.toArray(new String[0]));
        long totalRecordCnt = 0;
        while(it.hasNext()){
            ImportTemporalDataTx tx = it.next();
            PFieldList data = tx.getData();
            Set<String> prop = data.keysWithout("t", "u_sid");
            Integer time = data.get("t", 0).i();
            String eid = data.get("u_sid", 0).s();
            List<PVal> vals = new ArrayList<>();
            for(String p : prop){
                vals.add(data.get(p,0));
            }
            int t = time / step;
            long cnt = stat.count(eid, t, totalRecordCnt, vals.toArray(new PVal[0]));
            stat.vd.add((int) cnt);
            totalRecordCnt++;
        }
        System.out.println("entity\n"+stat.vd.toString(200));
        System.out.println(stat.mem);
        return Collections.emptyMap();
    }


    @Override
    public BenchmarkBuilder.BenchmarkRandomGen randomGen() {
        return new SynDataGenerator(dir, SynGenerateSchema.load("syn")).randomGen();
    }
}
