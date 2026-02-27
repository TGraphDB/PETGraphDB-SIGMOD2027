package edu.buaa.dataset;

import edu.buaa.common.utils.PVal;

import java.util.*;

public class DataStatistic<VAL> {

    private final String[] keys;
    public final ValueDistribution vd = new ValueDistribution();
    public final Memory<VAL> mem;

    public DataStatistic(String... keys) {
        this.keys = keys;
        this.mem = new Memory<>(keys.length);
    }

    @SafeVarargs
    public final long count(String entity, int time, long totalRecordCnt, VAL... values) {
        return mem.add(entity, time, totalRecordCnt, values);
    }

    public static class ValueDistribution {
        private final HashMap<Integer, Integer> val = new HashMap<>();

        public void add(int value) {
            val.merge(value, 1, Integer::sum);
        }


        public String toString(int bucketCnt) {
            ArrayList<Integer> arr = new ArrayList<>(val.keySet());
            arr.sort(Comparator.naturalOrder());
            TreeMap<Integer, Integer> tm = new TreeMap<>();
            for(int i=0; i<bucketCnt; i++){
                int j = i * arr.size() / bucketCnt;
                tm.put(arr.get(j), 0);
            }
            val.forEach((k,v)->{
                Integer kk = tm.floorKey(k);
                if(kk!=null) tm.put(kk, tm.get(kk)+v);
            });
            StringBuilder sb = new StringBuilder();
            for (Integer k : tm.keySet()) {
                sb.append(k).append("\t").append(tm.get(k)).append('\n');
            }
            return sb.toString();
        }

    }


    public static class Memory<T>{
        private final Map<String, MemoryItem> mem = new HashMap<>();
        private final List<Map<Integer, Integer>> cnt = new ArrayList<>();

        Memory(int cnt){
            //前面几项是计算属性非冗余的点边数，倒数第二项是计算record非冗余的点边数，最后一项是计算总record数。
            for(int i=0; i<=cnt+1; i++) this.cnt.add(new HashMap<>());
        }

        public long add(String eid, int time, long totalRecordCnt, T[] val){
            MemoryItem mContent = mem.get(eid);
            mem.put(eid, new MemoryItem(time, val, totalRecordCnt));
            cnt.get(cnt.size()-1).merge(time, 1, Integer::sum);
            int redundantCnt = 0;
            if(mContent!=null) {
                int i;
                for(i=0; i < val.length; i++){
                    boolean eq = eq(val[i], mContent.value[i]);
                    if(eq) redundantCnt++;
                    else cnt.get(i).merge(time, 1, Integer::sum);
                }
                if(redundantCnt == val.length) {
                    cnt.get(i).merge(time, 1, Integer::sum);
                }
                return totalRecordCnt - mContent.index;
            }else{
                return 0;
            }
        }

        private boolean eq(T a, T b){
            if(a instanceof Integer && b instanceof Integer){
                return (Integer) a == (Integer) b;
            }
            if(a instanceof Float && b instanceof Float){
                return Float.floatToRawIntBits((Float) a)==Float.floatToRawIntBits((Float) b);
//                return Math.abs((Float)a-(Float)b)<0.1f;
            }
            if(a instanceof PVal.IntVal && b instanceof PVal.IntVal){
                int aa = ((PVal.IntVal) a).i();
                int bb = ((PVal.IntVal) b).i();
                return aa == bb;
            }
            if(a instanceof PVal.FloatVal && b instanceof PVal.FloatVal){
                return Float.floatToRawIntBits(((PVal.FloatVal) a).f())==Float.floatToRawIntBits(((PVal.FloatVal) b).f());
//                return Math.abs(((PVal.FloatVal) a).f()-((PVal.FloatVal) b).f())<0.01f;
            }
            return false;
        }

        public String toString() {
            TreeSet<Integer> timeline = new TreeSet<>();
            for(Map<Integer, Integer> map : cnt){
                timeline.addAll(map.keySet());
            }
//            ArrayList<Integer> arr = new ArrayList<>(timeline);
//            TreeMap<Integer, Integer> tm = new TreeMap<>();
//            for(int i=0; i<bucketCnt; i++){
//                int j = i * arr.size() / bucketCnt;
//                tm.put(arr.get(j), 0);
//            }
//            val.forEach((k,v)->{
//                Integer kk = tm.floorKey(k);
//                if(kk!=null) tm.put(kk, tm.get(kk)+v);
//            });
            StringBuilder sb = new StringBuilder();
            for (Integer t : timeline) {
                sb.append(t);
                List<Integer> tmp = new ArrayList<>();
//                StringBuilder sb1 = new StringBuilder();
                for(Map<Integer, Integer> map : cnt){
                    Integer value = map.get(t);
                    tmp.add(value==null?0:value);
                    sb.append(String.format("% 7d", value));
                }
                for(int i=0; i<tmp.size()-1; i++){
                    float v = tmp.get(i) * 100f / tmp.get(tmp.size() - 1);
                    sb.append(String.format("\t%.2f%%", v));
                }
                sb.append('\n');
            }
            return sb.toString();
        }

        private class MemoryItem{
            private final int time;
            private final T[] value;
            private final long index;
            public MemoryItem(int time, T[] val, long index) {
                this.time = time;
                this.value = val;
                this.index = index;
            }
        }
    }
}
