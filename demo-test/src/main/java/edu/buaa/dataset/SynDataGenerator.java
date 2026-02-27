package edu.buaa.dataset;

import edu.buaa.common.benchmark.BenchmarkBuilder;
import edu.buaa.common.utils.PVal;
import edu.buaa.common.utils.SynGenerateSchema;
import edu.buaa.utils.Pair;
import edu.buaa.utils.TimeTicker;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class SynDataGenerator {
    public static int PARALLEL_CNT = 3;
    public static int Q_LENGTH = 4000;

    protected File dir;
    protected SynGenerateSchema schema;

    public SynDataGenerator(File dataRoot, SynGenerateSchema schema){
        this.dir = dataRoot;
        this.schema = schema;
    }


    public File prepareStaticCSV(boolean isNode) throws IOException{
        File fOut = new File(dir, isNode? "vertex.csv" : "edge.csv");
        int entityCnt = isNode ? schema.getNode().getCnt() : schema.getRel().getCnt();
        int nodeCnt = schema.getNode().getCnt();
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(fOut))) {
            writer.write("u_sid");
            if(!isNode){
                writer.write(",r_from,r_to");
            }
            writer.write('\n');
            Random rand = ThreadLocalRandom.current();
            for (int i = 1; i <= entityCnt; i++) {
                writer.write(Integer.toString(i));
                if(!isNode){
                    writer.write(',');
                    writer.write(Integer.toString(rand.nextInt(nodeCnt)+1));
                    writer.write(',');
                    writer.write(Integer.toString(rand.nextInt(nodeCnt)+1));
                }
                writer.write('\n');
            }
        }
        return fOut;
    }

    public File prepareTPCSV(boolean isNode) throws IOException {
        File fOut = new File(dir, isNode ? "vertex_tp.csv" : "edge_tp.csv");
        SynGenerateSchema.PropertySchema pSchema = isNode ? schema.getNode() : schema.getRel();
        int entityCnt = pSchema.getCnt();
        try (OutputStream writer = new BufferedOutputStream(new FileOutputStream(fOut))) {
            LinkedList<String> header = new LinkedList<>(pSchema.getName());
            header.addFirst("u_sid");
            header.addFirst("t");
            writer.write(String.join(",", header).getBytes(StandardCharsets.UTF_8));
            writer.write('\n');
            LinkedBlockingQueue<String> pipe = new LinkedBlockingQueue<>(Q_LENGTH*4);
            GenMaster gen = initClients(pipe, pSchema, entityCnt, schema.getStart(), schema.getEnd(), schema.isRepeat());
            gen.start();
            long size = 0;
            TimeTicker ticker = new TimeTicker(10, 20);
            int pause = 0;
            long pauseTotal = 0;
            ArrayList<String> arr = new ArrayList<>(Q_LENGTH+10);
            while(pause<1000){
                int cnt = pipe.drainTo(arr, Q_LENGTH);
                if(cnt>0){
                    byte[] content = String.join("\n", arr.subList(0, cnt)).getBytes(StandardCharsets.UTF_8);
                    writer.write(content);
                    writer.write('\n');
                    size += content.length+1;
                    pauseTotal += pause;
                    pause=0;
                }else{
                    TimeUnit.MILLISECONDS.sleep(5);
                    pause++;
                }
                if(ticker.shouldTick()) {
                    String line;
                    String p;
                    if(arr.size()>0) {
                        line = arr.get(0);
                        int t = Integer.parseInt(line.split(",")[0]);
                         p = String.format("%.1f", t * 100f / schema.getEnd());
                    } else {
                        line="";
                        p = "-";
                    }
                    System.out.println("Write "+String.format("%.1f GB(%s%%) in (%d) seconds. [line]%s", // (pause: %d)
                            size/1024f/1024f/1024f, p, ticker.duration()/1000, line
//                            , pauseTotal/1000
                    ));
                }
                arr.clear();
            }
            gen.close();
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new IOException(e);
        }
        return fOut;
    }

    private GenMaster initClients(LinkedBlockingQueue<String> pipe, SynGenerateSchema.PropertySchema schema,
                                  int cnt, int startT, int endT, boolean repeat) {
        List<Float> uP = schema.getUpdate();
        List<PVal.Type> tP = schema.getPValType();
        assert uP.size()== tP.size();
        Float eUp = schema.getEupdate();
        return new GenMaster(cnt, eUp, uP, tP, startT, endT, schema.getStep(), schema.getDelay(), repeat, pipe);
    }

    private static class GenMaster extends Thread{
        private final int cnt;
        private final float eupdate;
        private final Float[] updateP;
        private final PVal.Type[] typeMap;
        private final int startT;
        private final int endT;
        private final int delayT;
        private final int step;
        private final boolean repeat;
        private final LinkedBlockingQueue<String> pipe;
        private final ThreadPool runner;
        private final Child[] children;

        public GenMaster(int cnt, float eupdate, List<Float> updateP, List<PVal.Type> type, int startT, int endT,
                         int step, int delay, boolean repeat, LinkedBlockingQueue<String> pipe) {
            this.cnt = cnt;
            this.eupdate = eupdate;
            this.updateP = updateP.toArray(new Float[0]);
            this.typeMap = type.toArray(new PVal.Type[0]);
            this.startT = startT;
            this.endT = endT;
            this.delayT = delay;
            this.step = step;
            this.repeat = repeat;
            this.pipe = pipe;
            this.runner = new ThreadPool(PARALLEL_CNT, 12000); //生成点为4最佳，边为2最佳。似乎与单行长度有关
            children = new Child[cnt];
            for(int e=1; e<=cnt; e++) children[e-1] = new Child(e);
            Arrays.sort(children, Comparator.comparingInt(c -> c.curT));
        }

        @Override
        public void run(){
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for(int curT = startT; curT<endT; curT+=step){
                for(Child c : children) {
                    runner.submit(c);
                }
//                System.out.print(".");
//                int len = (int) (children.length*eupdate);
//                int l = random.nextInt(0, children.length-len);
//                int r = l + len;
//                for(int i=0; i<children.length; i++) {
//                    Child c = children[i];
//                    if(l<=i && i<=r){
            }
        }

        private class Child implements Runnable {
            private final int entity;
            private final String[] last;
            private int curT; //此处不需要volatile的原因是计算线程在pipe.put会插入memory barrier，故肯定会读到新的值。

            Child(int entity){
                this.entity = entity;
                this.last = new String[updateP.length];
                this.curT = startT+delay();
            }

            public void run(){
                ThreadLocalRandom random = ThreadLocalRandom.current();
                if(random.nextFloat()>=eupdate) {
                    curT += step;
                    return;
                }
                boolean hasUpdate = false;
                LinkedList<String> row = new LinkedList<>();
                for (int i = 0; i < updateP.length; i++) {
                    if (random.nextFloat() < updateP[i] || last[i] == null) {
                        last[i] = typeMap[i] == PVal.Type.INT ? Integer.toString(random.nextInt(1000)) : Float.toString(random.nextFloat());
                        hasUpdate = true;
                    }
                    row.add(last[i]);
                }
                if (repeat || hasUpdate) try {
                    row.addFirst(Integer.toString(entity));
                    row.addFirst(Integer.toString(curT));
                    String result = String.join(",", row);
                    pipe.put(result);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                curT += step;
            }

            private int delay(){
                ThreadLocalRandom rand = ThreadLocalRandom.current();
                double g;
                do{ g = Math.abs(rand.nextGaussian()) / 4; }while(g>=1);
                return (int) (g*delayT);
            }
        }

        public void close() throws InterruptedException {
            this.runner.close();
        }
    }

    private static class ThreadPool extends ThreadPoolExecutor{

        public ThreadPool(int size, int capacity) {
            super(0, size, 5, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(capacity), new ThreadPoolExecutor.CallerRunsPolicy());
            this.allowCoreThreadTimeOut(true);
        }

        public boolean hasTask(){
            return this.getActiveCount()>0 || this.getQueue().size()>0;
        }

        public void close() throws InterruptedException {
            while(this.hasTask()){
                TimeUnit.SECONDS.sleep(10);
            }
            this.shutdown();
            while(!this.awaitTermination(10, TimeUnit.SECONDS)){
                TimeUnit.SECONDS.sleep(10);
            }
        }
    }

    public File prepareEmptyTPCSV(boolean isNode) throws IOException {
        File fOut = new File(dir, isNode ? "vertex_tp.csv" : "edge_tp.csv");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fOut))) {
            LinkedList<String> header = new LinkedList<>();
            header.addFirst("u_sid");
            header.addFirst("t");
            writer.write(String.join(",", header));
            writer.write('\n');
        }
        return fOut;
    }

//    public File prepareTPCSV(boolean isNode) throws IOException {
//        File fOut = new File(dir, isNode ? "vertex_tp.csv" : "edge_tp.csv");
//        SynGenerateSchema.PropertySchema pSchema = isNode ? schema.getNode() : schema.getRel();
//        int entityCnt = isNode ? schema.getNode().getCnt() : schema.getRel().getCnt();
//        int step = pSchema.getStep();
//        float entityUpdateP = pSchema.getEupdate();
//        List<String> props = pSchema.getName();
//        List<Float> updateP = pSchema.getUpdate();
//        List<PVal.Type> type = pSchema.getPValType();
//        try(BufferedWriter writer = new BufferedWriter(new FileWriter(fOut))){
//            LinkedList<String> header = new LinkedList<>(pSchema.getName());
//            header.addFirst("u_sid");
//            header.addFirst("t");
//            writer.write(String.join(",", header));
//            writer.write('\n');
////        Map<String, PVal.Type> map = isNode ? schema.nodeTemporal : schema.relTemporal;
////        Map<String, Float> updateP = isNode ? schema.nTpUpdate : schema.rTpUpdate;
////        float entityUpdateP = isNode ? schema.nodeUpdate : schema.edgeUpdate;
//            Map<Pair<Integer, String>, String> last = new HashMap<>();
//            Random random = ThreadLocalRandom.current();
//            for(int curT = schema.getStart(); curT<schema.getEnd(); curT+=step){
//                for(int e=1; e<=entityCnt; e++){
//                    if(random.nextFloat()<entityUpdateP){
//                        ArrayList<String> row = new ArrayList<>();
//                        row.add(Integer.toString(curT));
//                        row.add(Integer.toString(e));
//                        for(int p=0; p<props.size(); p++){
//                            String prop = props.get(p);
//                            String val;
//                            Pair<Integer, String> id = Pair.of(e, prop);
//                            if(random.nextFloat()<updateP.get(p) || last.get(id)==null){
//                                val = type.get(p)==PVal.Type.INT ? Integer.toString(random.nextInt(1000)) : Float.toString(random.nextFloat());
//                                last.put(id, val);
//                            }else{
//                                val = last.get(id);
//                            }
//                            row.add(val);
//                        }
//                        writer.write(String.join(",", row));
//                        writer.write('\n');
//                    }
//                }
//                System.out.print('.');
//            }
//        }
//        return fOut;
//    }



    public BenchmarkBuilder.BenchmarkRandomGen randomGen() {
        Random rand = ThreadLocalRandom.current();
        return new BenchmarkBuilder.BenchmarkRandomGen() {
            @Override
            public boolean nodeOrEdge() {
                return rand.nextBoolean();
            }

            @Override
            public String entity(boolean isNode) {
                SynGenerateSchema.PropertySchema ps = isNode ? schema.getNode() : schema.getRel();
                return String.valueOf( rand.nextInt(ps.getCnt())+1 );
            }

            @Override
            public String prop(boolean isNode) {
                SynGenerateSchema.PropertySchema ps = isNode ? schema.getNode() : schema.getRel();
                return ps.getName().get(rand.nextInt(ps.getName().size()));
            }

            @Override
            public List<PVal> valueIntervalStart(boolean isNode, String prop) {
                Map<String, PVal.Type> m = map(isNode);
                int[] arr = new int[]{0, 25, 50, 75};
                if(m.get(prop)== PVal.Type.INT) {
                    return Arrays.stream(arr).mapToObj(PVal::i).collect(Collectors.toList());
                }else{
                    return Arrays.stream(arr).mapToObj(ff->PVal.f(ff/100f)).collect(Collectors.toList());
                }
            }

            @Override
            public Pair<PVal, PVal> valueRange(boolean isNode, String prop) {
                Map<String, PVal.Type> m = map(isNode);
                if(m.get(prop)== PVal.Type.INT) {
                    return Pair.of(PVal.i(40), PVal.i(60));
                }else {
                    return Pair.of(PVal.f(.4f), PVal.f(.6f));
                }
            }

            @Override
            public int snapshotInterval(boolean isNode) {
                return isNode ? schema.getNode().getStep() : schema.getRel().getStep();
            }

            private Map<String, PVal.Type> nm=null, em=null;
            private Map<String, PVal.Type> map(boolean isNode){
                Map<String, PVal.Type> m = isNode ? nm : em;
                SynGenerateSchema.PropertySchema ps = isNode ? schema.getNode() : schema.getRel();
                if(m==null){
                    m = new HashMap<>();
                    List<PVal.Type> tLst = ps.getPValType();
                    for(int i=0; i<tLst.size(); i++) m.put(ps.getName().get(i), tLst.get(i));
                    if(isNode) this.nm = m;
                    else this.em = m;
                }
                return m;
            }
        };
    }
}