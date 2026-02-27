package edu.buaa.common.utils;

import org.dflib.DataFrame;
import org.dflib.Series;
import org.dflib.print.TabularPrinter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class PFieldList {
    private final Map<String, List<Object>> data = new HashMap<>();

    public int add(String key, PVal v){
        List<Object> lst = data.get(key);
        if(lst==null){
            lst = new CustomArrayList<>();
            lst.add(v.getVal());
            data.put(key, lst);
        }else{
            lst.add(v.getVal());
        }
        return lst.size();
    }

    public void set(String key, PVal v, int index){
        List<Object> lst = data.get(key);
        if(lst==null){
            throw new IllegalStateException("key not found: "+key);
        }else{
            lst.set(index, v.getVal());
        }
    }

    public int add(String key, Object v){
        List<Object> lst = data.get(key);
        if(lst==null){
            lst = new CustomArrayList<>();
            lst.add(v);
            data.put(key, lst);
        }else{
            lst.add(v);
        }
        return lst.size();
    }

    public int size(){
        int size = -1;
        for(String k : data.keySet()){
            if(size==-1) size = data.get(k).size();
            else assert size == data.get(k).size():"expect size="+size+" but got "+data.get(k).size()+" instead.";
        }
        return size;
    }

    public Set<String> keys(){
        return data.keySet();
    }

    public Set<String> keysWithout(String... exclude){
        Set<String> s = new HashSet<>(data.keySet());
        Arrays.asList(exclude).forEach(s::remove);
        return s;
    }

    public PVal get(String key, int index) {
        List<Object> lst = data.get(key);
        if(lst==null) throw new IllegalStateException("key "+key+" not found in PFieldList. available: "+data.keySet());
        else return PVal.v(lst.get(index));
    }

    public PFieldList head(int lineCnt) {
        PFieldList result = new PFieldList();
        for (Map.Entry<String, List<Object>> entry : data.entrySet()) {
            String s = entry.getKey();
            CustomArrayList<Object> arr = (CustomArrayList<Object>) entry.getValue();
            result.data.put(s, arr.shiftLeft(lineCnt));
        }
        return result;
    }

    public PFieldList slice(int from, int to) {
        PFieldList result = new PFieldList();
        for (Map.Entry<String, List<Object>> entry : data.entrySet()) {
            String s = entry.getKey();
            CustomArrayList<Object> arr = (CustomArrayList<Object>) entry.getValue();
            result.data.put(s, arr.subList(from, to));
        }
        return result;
    }

    public Map<String, List<Object>> getData() {
        return data;
    }

    public void append(PFieldList p) {
        for (String s : p.data.keySet()) data.putIfAbsent(s, new CustomArrayList<>());
        for (String s : data.keySet()) {
            data.get(s).addAll(p.getData().get(s));
        }
    }

    public void sortBy(String key, boolean ascending) {
        String[] columnNames = data.keySet().toArray(String[]::new);
        DataFrame df = DataFrame.byColumn(columnNames).ofIterable(
                Arrays.stream(columnNames).map(name -> Series.ofIterable(data.get(name))).collect(Collectors.toList()));

        DataFrame df1 = df.sort(key, ascending);

//        System.out.println("SORT DATA BY "+key+":\n"+new TabularPrinter(20, 11).toString(df1));

        for(String cName: columnNames){
            Series<Object> col = df1.getColumn(cName);
            data.put(cName, new CustomArrayList<>(col.toList()));
        }
    }

    public String printByOrder(int colNum, int rowNum, String... columnNames) {
        for(String col : columnNames){
            if(data.get(col)==null) throw new IllegalArgumentException("name "+col+" not found in data. available: "+data.keySet());
        }
        DataFrame df = DataFrame.byColumn(columnNames).ofIterable(
                Arrays.stream(columnNames).map(name -> Series.ofIterable(data.get(name))).collect(Collectors.toList()));
        boolean[] asc = new boolean[columnNames.length];
        Arrays.fill(asc, true);
        DataFrame df1 = df.sort(columnNames, asc);

        return new TabularPrinter(rowNum, colNum).toString(df1);
    }

    private static class CustomArrayList<V> extends ArrayList<V>{
        public CustomArrayList(List<V> content) {
            super(content);
        }

        public CustomArrayList() {
            super();
        }

        public CustomArrayList<V> shiftLeft(int k){
            CustomArrayList<V> arr = new CustomArrayList<>(subList(0, k));
            removeRange(0, k);
            return arr;
        }
    }
}
