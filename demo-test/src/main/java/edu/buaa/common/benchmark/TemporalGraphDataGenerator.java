package edu.buaa.common.benchmark;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.PeekingIterator;
import edu.buaa.common.transaction.ImportStaticDataTx;
import edu.buaa.common.transaction.ImportTemporalDataTx;
import edu.buaa.common.transaction.UpdateTemporalDataTx;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.utils.TPDataFileFormatConvertor;

import java.io.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

public abstract class TemporalGraphDataGenerator implements SQLMilestoneBuilder.CSVExport {
    protected File dir;
    protected TemporalGraphPropertySchema schema;
    protected int timeShiftInTp = 0;
    protected boolean sectionEnable = true;

    public TemporalGraphDataGenerator init(File dataRoot, TemporalGraphPropertySchema schema){
        this.dir = dataRoot;
        this.schema = schema;
        return this;
    }

    public boolean isSectionEnable() {
        return sectionEnable;
    }

    public void setSectionEnable(boolean sectionEnable) {
        this.sectionEnable = sectionEnable;
    }

    abstract public Iterator<ImportStaticDataTx> readNetwork(int size) throws IOException;

    abstract public PeekingIterator<ImportTemporalDataTx> readNodeTemporal(String startDay, String endDay, int linePerTx) throws IOException;

    public abstract Iterator<ImportTemporalDataTx> readNodeTemporal(String beginTime, int txSize) throws IOException;

    abstract public PeekingIterator<ImportTemporalDataTx> readRelTemporal(String startDay, String endDay, int linePerTx) throws IOException;

    abstract public PeekingIterator<ImportTemporalDataTx> readRelTemporal(String startTime, int linePerTx) throws IOException;

    abstract public int parseTime(String time);

    public void setTimeShift(String time){
        this.timeShiftInTp = parseTime(time);
    }

    public abstract BenchmarkBuilder.BenchmarkRandomGen randomGen() throws IOException;

    public abstract Map<String, Object> stat(boolean isNode, String beginT, String endT) throws IOException;

    public PeekingIterator<ImportTemporalDataTx> packUnique(PeekingIterator<ImportTemporalDataTx> it){
        return new TransactionTimeBulkLoad(new GroupByTime(it, 81920));
    }

    private static class GroupByTime extends AbstractIterator<ImportTemporalDataTx> implements PeekingIterator<ImportTemporalDataTx> {

        private final Iterator<ImportTemporalDataTx> iterator;
        private final int count;

        private ImportTemporalDataTx tx;
        private PFieldList data2pack = new PFieldList();

        public GroupByTime(Iterator<ImportTemporalDataTx> iterator, int count){
            this.iterator = iterator;
            this.count = count;
        }

        @Override
        protected ImportTemporalDataTx computeNext() {
            while (iterator.hasNext()) {
                tx = iterator.next();
                data2pack.append(tx.getData());
                if (data2pack.size() > count) {
                    data2pack.sortBy("t", true);
                    ImportTemporalDataTx tx0 = new ImportTemporalDataTx();
                    tx0.setSection(tx.getSection());
                    tx0.setNode(tx.isNode());
                    tx0.setData(this.data2pack);
                    this.data2pack = new PFieldList();
                    return tx0;
                }
            }
            if (data2pack.size() > 0) {
                ImportTemporalDataTx tx0 = new ImportTemporalDataTx();
                tx0.setSection(tx.getSection());
                tx0.setNode(tx.isNode());
                tx0.setData(this.data2pack);
                this.data2pack = new PFieldList();
                return tx0;
            }
            return endOfData();
        }
    }

    /**
     * packs data in a batch which each u_sid is unique.
     * result: every update of an u_sid is recorded.
     * Impl: read until next repeat u_sid.
     *
     * sjh 2026-02-17
     */
    private static class TransactionTimeBulkLoad extends AbstractIterator<ImportTemporalDataTx> implements PeekingIterator<ImportTemporalDataTx> {

        private final Iterator<ImportTemporalDataTx> iterator;
        private final Set<String> u_sidSet = new HashSet<>();
        private ImportTemporalDataTx lastTx = null;
        private int lastRemainIndex = 0;
        private PFieldList data2pack = new PFieldList();

        public TransactionTimeBulkLoad(Iterator<ImportTemporalDataTx> iterator){
            this.iterator = iterator;
        }

        @Override
        protected ImportTemporalDataTx computeNext() {
            if(lastTx!=null) {
                ImportTemporalDataTx tx = process(lastTx);
                if(tx!=null) {
                    return tx;
                }else{
                    lastTx = null;
                }
            }
            while(iterator.hasNext()) {
                lastTx = iterator.next();
                ImportTemporalDataTx tx = process(lastTx);
                if(tx!=null) {
                    return tx;
                }
            }
            if(lastTx!=null) {
                ImportTemporalDataTx tx = pack(lastTx, 0, 0);
                lastTx = null;
                return tx;
            }
            return endOfData();
        }

        private ImportTemporalDataTx process(ImportTemporalDataTx tx){
            int tSize = tx.getData().size();
            int index = loopUntilSameId(tx, lastRemainIndex, tSize, u_sidSet);
            if(index==-1){
                data2pack.append(tx.getData().slice(lastRemainIndex, tSize));
                lastRemainIndex = 0;
                return null;
            }else{
                ImportTemporalDataTx tx0 = pack(lastTx, lastRemainIndex, index);
                lastRemainIndex = index;
                return tx0;
            }
        }

        private int loopUntilSameId(ImportTemporalDataTx tx, int begin, int end, Set<String> u_sidSet){
            PFieldList data = tx.getData();
            for (int i = begin; i < end; i++) {
                String u_sid = data.get("u_sid", i).s();
                if (u_sidSet.contains(u_sid)) {
                    return i;
                } else {
                    u_sidSet.add(u_sid);
                }
            }
            return -1; // all ids are unique
        }

        private ImportTemporalDataTx pack(ImportTemporalDataTx tx, int begin, int end){
            ImportTemporalDataTx tx0 = new ImportTemporalDataTx();
            tx0.setSection(tx.getSection());
            tx0.setNode(tx.isNode());

            this.data2pack.append(tx.getData().slice(begin, end));
            tx0.setData(this.data2pack);
            this.lastRemainIndex = end;
            this.data2pack = new PFieldList();
            this.u_sidSet.clear();
            return tx0;
        }
    }

    public void countCommonTi(boolean isNode, String dataSize, String beginT, String endT, int step) throws Exception{
        this.readNetwork(1000);
        PeekingIterator<ImportTemporalDataTx> it = isNode ? readNodeTemporal(beginT, endT, 1) : readRelTemporal(beginT, endT, 1);
        TPDataFileFormatConvertor.P2IConv conv = new TPDataFileFormatConvertor.P2IConv(it, Integer.MAX_VALUE, 1, isNode);
        int snapshot = -1;
        int cnt = 0;
        HashSet<Long> set = new HashSet<>();
        while (conv.hasNext()) {
            PFieldList data = conv.next().getData();
            long st = data.get("st", 0).i();
            int curSnapshot = (int) (st / step);
            if(snapshot==-1) snapshot = curSnapshot;
            if (snapshot == curSnapshot) {
                int et = data.get("et", 0).i();
                long v = (st << 32) + et;
                set.add(v);
                cnt++;
            }else{
                System.out.println(snapshot+" distinct: " + set.size() + " total: " + cnt +" ratio: "+(set.size()*100L/ (cnt==0?1:cnt))+"%");
                snapshot = curSnapshot;
                set.clear();
                cnt = 0;
            }
        }
    }


    HashMap<String, Long> nIdMap = new HashMap<>();
    HashMap<String, Long> rIdMap = new HashMap<>();

    @Override
    public File prepareStaticCSV(boolean isNode) throws Exception {
        LinkedList<String> p;
        File f;
        if(isNode) {
            f = new File(dir, "vertex_export.csv");
            p = order(schema.nodeStatic);
            p.addFirst("id");
            nIdMap.clear();
        }else{
            f = new File(dir, "edge_export.csv");
            p = order(schema.relStatic);
            p.addFirst("r_to");
            p.addFirst("r_from");
            p.addFirst("id");
            rIdMap.clear();
        }
        if(f.exists() && f.length()>1000) {
            fillMap(f, isNode ? nIdMap : rIdMap, isNode ? 1 : 3);
            return f;
        }
        Iterator<ImportStaticDataTx> it = readNetwork(1000);
        try(BufferedWriter w = new BufferedWriter(new FileWriter(f))){
            w.write(String.join(",", p));
            w.write('\n');
            p.removeFirst();
            while(it.hasNext()){
                ImportStaticDataTx tx = it.next();
                if(isNode) {
                    writeNodeLine(tx.getNodes(), p, w, nIdMap);
                }else {
                    writeRelLine(tx.getRels(), p, w, nIdMap, rIdMap);
                }
            }
        }
        return f;
    }

    protected void fillMap(File f, Map<String, Long> map, int uSidIndex) throws IOException {
        try(BufferedReader reader = new BufferedReader(new FileReader(f))){
            reader.readLine(); // skip header: id, u_sid
            String line;
            while((line=reader.readLine())!=null){
                String[] arr = line.split(",");
                map.put(arr[uSidIndex], Long.parseLong(arr[0]));
            }
        }
    }

    private void writeNodeLine(PFieldList data, LinkedList<String> fields, BufferedWriter writer, Map<String, Long> idMap) throws IOException {
        int dataSize = data.size();
        for(int i=0; i<dataSize; i++) {
            long id = idMap.size() + 1L;
            StringBuilder sb = new StringBuilder(Long.toString(id));
            sb.append(',');
            for(String prop : fields) {
                String f = data.get(prop, i).toString();
                sb.append(f).append(',');
                if("u_sid".equals(prop)){
                    idMap.put(f, id);
                }
            }
            sb.setLength(sb.length()-1);
            writer.write(sb.toString());
            writer.write('\n');
        }
    }

    private void writeRelLine(PFieldList data, LinkedList<String> fields, BufferedWriter writer, Map<String, Long> nodeIdMap, Map<String, Long> relMap) throws IOException {
        int dataSize = data.size();
        for(int i=0; i<dataSize; i++) {
            long id = relMap.size() + 1L;
            StringBuilder sb = new StringBuilder(Long.toString(id));
            sb.append(',');
            for(String prop : fields) {
                String f = data.get(prop, i).toString();
                if(prop.equals("r_from") || prop.equals("r_to")){
                    sb.append(nodeIdMap.get(f)).append(',');
                }else{
                    sb.append(f).append(',');
                }
                if("u_sid".equals(prop)){
                    relMap.put(f, id);
                }
            }
            sb.setLength(sb.length()-1);
            writer.write(sb.toString());
            writer.write('\n');
        }
    }

    @Override
    public File prepareTPCSV(String dataSize, String beginT, String endT, boolean isNode, boolean isTimeInterval, boolean isTimestamp) throws Exception {
        File file = new File(dir, (isNode ? "vertex" : "edge") +"_temporal_export_"+dataSize.toLowerCase().replace("t.", "")+
                (isTimeInterval?".ti":".tp")+(isTimestamp?".timestamp":"")+".csv");
        System.out.println(beginT+"~"+endT);
        if(file.exists() && file.length()>10) return file;
        PeekingIterator<ImportTemporalDataTx> it = isNode ? readNodeTemporal(beginT, endT, 1000) : readRelTemporal(beginT, endT, 1000);
        LinkedList<String> proName = order(isNode ? schema.nodeTemporal : schema.relTemporal);
        proName.addFirst("entity");
        HashMap<String, Long> idMap = isNode ? nIdMap : rIdMap;
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            if(isTimeInterval){
                writeTimeInterval(proName, it, writer, isNode, isTimestamp, idMap);
            }else{
                writeTimePoint(proName, it, writer, isTimestamp, idMap);
            }
        }
        return file;
    }

    private void writeTimePoint(LinkedList<String> proName, PeekingIterator<ImportTemporalDataTx> it, BufferedWriter writer, boolean isTimestamp, Map<String, Long> idMap) throws IOException {
        proName.addFirst("t");
        writer.write(String.join(",", proName));
        writer.write('\n');
        while (it.hasNext()) {
            ImportTemporalDataTx tx = it.next();
            writeTpLine(tx.getData(), proName, writer, isTimestamp, idMap);
        }
    }

    public void writeTimeInterval(LinkedList<String> proName, PeekingIterator<ImportTemporalDataTx> it, BufferedWriter writer, boolean isNode, boolean isTimestamp, Map<String, Long> idMap) throws IOException {
        proName.addFirst("et");
        proName.addFirst("st");
        writer.write(String.join(",", proName));
        writer.write('\n');
        TPDataFileFormatConvertor.P2IConv conv = new TPDataFileFormatConvertor.P2IConv(it, Integer.MAX_VALUE, 1000, isNode);
        while (conv.hasNext()) {
            UpdateTemporalDataTx tx = conv.next();
            writeTpLine(tx.getData(), proName, writer, isTimestamp, idMap);
        }
    }

    protected void writeTpLine(PFieldList fields, LinkedList<String> proName, BufferedWriter writer,
                             boolean isTimestamp, Map<String, Long> idMap) throws IOException {
        int dataSize = fields.size();
        for(int i=0; i<dataSize; i++) {
            StringBuilder sb = new StringBuilder();
            for(String prop : proName) {
                if("entity".equals(prop)){
                    String usid = fields.get("u_sid", i).s();
                    Long eid = idMap.get(usid);
                    sb.append(eid).append(',');
                }else if(isTimestamp && ("t".equals(prop) || "st".equals(prop) || "et".equals(prop))){
                    sb.append(new Timestamp(fields.get(prop, i).i() * 1000L)).append(',');
                }else {
                    sb.append(fields.get(prop, i).toString()).append(',');
                }
            }
            sb.setLength(sb.length()-1);
            writer.write(sb.toString());
            writer.write('\n');
        }
    }

    private LinkedList<String> order(Map<String, PVal.Type> typeMap) {
        return new LinkedList<>(typeMap.keySet());
    }

    public static class BucketSectionMap {
        private final Map<Integer, PFieldList> bucket = new HashMap<>();
        private LinkedList<Map.Entry<Integer, PFieldList>> remains = null;
        public PFieldList getBySection(int section){
            return bucket.computeIfAbsent(section, i->new PFieldList());
        }
        public void rm(int section){
            bucket.put(section, null);
        }

        public LinkedList<Map.Entry<Integer, PFieldList>> getRemaining() {
            if(remains==null) {
                remains = bucket.entrySet().stream().filter(e -> e.getValue() != null).collect(Collectors.toCollection(LinkedList::new));
                bucket.clear();
            }
            return remains;
        }
    }

}
