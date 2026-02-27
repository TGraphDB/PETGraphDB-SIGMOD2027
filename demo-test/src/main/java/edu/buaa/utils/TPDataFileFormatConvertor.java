package edu.buaa.utils;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.PeekingIterator;
import edu.buaa.common.transaction.ImportTemporalDataTx;
import edu.buaa.common.transaction.UpdateTemporalDataTx;
import edu.buaa.common.utils.PFieldList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipException;

public interface TPDataFileFormatConvertor {

    class TimeIntervalConv extends AbstractIterator<String> implements AutoCloseable{
        private final Map<String, String[]> lastStatus = new HashMap<>();
        private final String headerLine;
        private final Map<String, Long> idMap;
        private LinkedList<Map.Entry<String, String[]>> remains = null;
        private BufferedReader reader;
        private final int startT;
        private final int endT;
        private final boolean timestamp;

        public TimeIntervalConv(File input, int startT, int endT, boolean timestamp, Map<String, Long> idMap) throws IOException {
            this.idMap = idMap;
            try {
                reader = Helper.gzipReader(input);
            }catch (ZipException e){
                reader = new BufferedReader(new FileReader(input));
            }
            this.startT = startT;
            this.endT = endT;
            this.timestamp = timestamp;
            headerLine = reader.readLine();
        }

        @Override
        protected String computeNext() {
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    String[] arr = line.split(",");
                    int time = Integer.parseInt(arr[0]);
                    if (time >= endT) break;
                    String id = arr[1];
                    if (time >= startT) {
                        String[] last = lastStatus.get(id);
                        if (last != null) {
                            return output(id, time, last);
                        }
                    }
                    lastStatus.put(id, arr);
                }
                if(remains==null){
                    remains = new LinkedList<>(lastStatus.entrySet());
                }
                Map.Entry<String, String[]> e = remains.pollFirst();
                if(e!=null){
                    return output(e.getKey(), endT+1L, e.getValue());
                }
                return endOfData();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private String output(String id, long time, String[] arr) {
            int lastT =Math.max(Integer.parseInt(arr[0]), startT);
            Long idLong = idMap.get(id);
            assert idLong!=null;
            StringBuilder sb = new StringBuilder(String.valueOf(idLong));
            sb.append(',');
            if(timestamp) {
                sb.append(new Timestamp(lastT*1000L)).append(',').
                        append(new Timestamp((time - 1)*1000L)).append(',');
            }else {
                sb.append(lastT).append(',').append(time - 1).append(',');
            }
            for(int i=2; i<arr.length; i++){
                sb.append(arr[i]).append(',');
            }
            sb.setLength(sb.length()-1);
            return sb.toString();
        }

        @Override
        public void close() throws Exception {
            reader.close();
        }

        public String getHeaderLine() {
            LinkedList<String> arr = new LinkedList<>(Arrays.asList(headerLine.split(",")));
            arr.removeFirst();
            arr.removeFirst();
            arr.addFirst("en_time");
            arr.addFirst("st_time");
            arr.addFirst("entity");
            return String.join(",", arr);
        }
    }

    class TimePointConv extends AbstractIterator<String> implements AutoCloseable{
        private final String headerLine;
        private final Map<String, Long> idMap;
        private BufferedReader reader;
        private final int startT;
        private final int endT;
        private final boolean timestamp;
        public TimePointConv(File input, int startT, int endT, boolean timestamp, Map<String, Long> idMap) throws IOException {
            this.idMap = idMap;
            try {
                reader = Helper.gzipReader(input);
            }catch (ZipException e){
                reader = new BufferedReader(new FileReader(input));
            }
            this.startT = startT;
            this.endT = endT;
            this.timestamp = timestamp;
            headerLine = reader.readLine();
        }

        @Override
        protected String computeNext() {
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    String[] arr = line.split(",");
                    int time = Integer.parseInt(arr[0]);
                    if (time >= endT) break;
                    if (time >= startT) {
                        return output(arr);
                    }
                }
                return endOfData();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private String output(String[] arr) {

            return null;
        }

        @Override
        public void close() throws Exception {
            reader.close();
        }

        public String getHeaderLine() {
            return headerLine;
        }
    }



    class P2IConv extends AbstractIterator<UpdateTemporalDataTx>{
        private final Map<String, Map<String, Object>> lastStatus = new HashMap<>();
        private final PeekingIterator<ImportTemporalDataTx> input;
        private LinkedList<Map<String, Object>> remains = null;
        private final PFieldList output = new PFieldList();

        private final int endT;
        private final int outputSize;
        private final boolean isNode;
        public P2IConv(PeekingIterator<ImportTemporalDataTx> input, int endT, int size, boolean isNode){
            this.input = input;
            this.endT = endT;
            this.outputSize = size;
            this.isNode = isNode;
        }

        @Override
        protected UpdateTemporalDataTx computeNext() {
            while (input.hasNext()) {
                ImportTemporalDataTx tx = input.next();
                assert tx.isNode()==isNode;
                PFieldList data = tx.getData();
                int size = data.size();
                for(int i=0; i<size; i++) {
                    String id = data.get("u_sid", i).s();
                    Map<String, Object> last = lastStatus.get(id);
                    if (last != null) {
                        int time = data.get("t", i).i();
                        lastStatus.put(id, getProp(data, i));
                        output(time-1, last);
                    }else {
                        lastStatus.put(id, getProp(data, i));
                    }
                }
                if(this.output.size()>=this.outputSize) {
                    return new UpdateTemporalDataTx(this.output.head(this.outputSize), isNode);
                }
            }
            if(remains==null){
                remains = new LinkedList<>(lastStatus.values());
            }
            int i;
            for(i=this.output.size(); i<this.outputSize && remains.size()>0; i++) {
                output(endT, remains.pollFirst());
            }
            if(i>0) {
                return new UpdateTemporalDataTx(this.output.head(i), isNode);
            }else {
                return endOfData();
            }
        }

        private Map<String, Object> getProp(PFieldList data, int i) {
            Map<String, Object> arr = new HashMap<>();
            for(String key: data.keys()){
                arr.put(key, data.get(key, i).getVal());
            }
            return arr;
        }

        private void output(int endT, Map<String, Object> arr) {
            Object v = arr.remove("t");
            int beginT = (int) v;
            arr.forEach(output::add);
            output.add("st", beginT);
            output.add("et", endT);
        }
    }

    class P2IConvAsync extends Thread{
        private final LinkedBlockingQueue<UpdateTemporalDataTx> pipe;
        private final P2IConv conv;
        private volatile boolean productDone = false;

        public P2IConvAsync(PeekingIterator<ImportTemporalDataTx> input, int endT, int size, boolean isNode, int capacity) {
            this.conv = new P2IConv(input, endT, size, isNode);
            this.pipe = new LinkedBlockingQueue<>(capacity);
            this.setDaemon(true);
        }

        public void run(){
            try {
                while (conv.hasNext()) pipe.put(conv.next());
                productDone = true;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public boolean done(){
            return productDone;
        }

        public UpdateTemporalDataTx next(int waitSeconds) throws InterruptedException {
            return pipe.poll(waitSeconds, TimeUnit.SECONDS);
        }
    }

}
