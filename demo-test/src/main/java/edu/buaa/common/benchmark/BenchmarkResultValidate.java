package edu.buaa.common.benchmark;

import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.transaction.EntityHistoryTx;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Pair;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public class BenchmarkResultValidate {
//    private static final String resultDir = "D:\\tgraph\\test\\energy\\benchmark\\b0.5y-100";
    private static final String resultPath = Helper.mustEnv("BENCHMARK_DIR");

    public static void main(String[] args) throws Exception {
        BenchmarkResultValidate v = new BenchmarkResultValidate();
//        v.validateResult();
        System.out.println("############ History print for EntityHistoryTx queries #############");
        v.historyPrint();
    }

    List<String> dbList = new ArrayList<>();

    private List<BenchmarkReader> txResultReaders() throws IOException {
        List<BenchmarkReader> fileList = new ArrayList<>();
        File dir = new File(resultPath);
        if(!dir.exists()) throw new IllegalArgumentException("benchmark dir not exist.");
        String[] content = dir.list();
        if(content==null) throw new IllegalArgumentException("");
        for(String file : content){
            if(file.contains("result")) {
                fileList.add(new BenchmarkReader(new File(dir, file), false));
                dbList.add(file.replace(".result.json", ""));
            }
        }
        return fileList;
    }

    public void validateResult() throws Exception {
        List<BenchmarkReader> readers = txResultReaders();
        List<AbstractTransaction> tx;
        int cnt = -1, line = 0;
        try{
            do {
                tx = next(readers);
                line++;
                if (cnt < 0) cnt = tx.size();
                else if (cnt == tx.size()) {
                    validate(tx, line);
                } else if (!tx.isEmpty()) {
                    throw new RuntimeException("size not equal at line " + line + "! expect " + cnt + " but got " + tx.size());
                }
            } while(!tx.isEmpty());
        }catch (RuntimeException | AssertionError e){
            System.out.println("validate line: "+line);
            e.printStackTrace();
        }
    }

    private List<AbstractTransaction> next(List<BenchmarkReader> readers) throws IOException {
        List<AbstractTransaction> result = new ArrayList<>();
        for(BenchmarkReader r : readers){
            if(r.hasNext()) result.add(r.next());
            else{
                System.out.println("EOF "+r.file);
                r.close();
            }
        }
        return result;
    }

    private void validate(List<AbstractTransaction> list, int line){
        AbstractTransaction.TxType t = list.get(0).getTxType();
        assert list.stream().allMatch(tx -> tx.getTxType().equals(t));
        StringBuilder sb0 = new StringBuilder();
        StringBuilder sbx = new StringBuilder();
        for (int i=0; i<list.size()-1; i++) {
            for(int j=i+1; j<list.size(); j++){
                AbstractTransaction tx0 = list.get(i);
                AbstractTransaction tx1 = list.get(j);
                try {
                    tx0.validateResult(tx1.getResult());
                    sb0.append(dbList.get(i)).append("=").append(dbList.get(j)).append(", ");
                }catch (Helper.SetNotMath e){
                    sbx.append(dbList.get(i)).append("<>").append(dbList.get(j)).append(' ').append(e.getMessage()).append('\n');
                } catch (RuntimeException e){
                    System.out.println("RuntimeException during "+ dbList.get(i)+" vs "+ dbList.get(j)+" on line "+line);
                    throw e;
                }
            }
        }
        if(sbx.length()>0){
            System.out.println("ERR: NOT Match on line "+line+". <"+t+">  "+sb0);
            System.out.println(sbx);
        }
    }

    public void historyPrint() throws IOException {
        List<List<AbstractTransaction>> sys = toList(txResultReaders());
        int size = -1;
        for(int i=0; i< sys.size(); i++){
            if(i==0) size=sys.get(i).size();
            else if(size!=sys.get(i).size()){
                System.out.println("expect size "+size+" but got "+sys.get(i).size()+" on "+ dbList.get(i));
                size = Math.min(size, sys.get(i).size());
            }
        }
        for(int line=0; line<size; line++){
            List<List<Pair<Integer, PVal>>> res = new ArrayList<>();
            for (int i = 0; i < sys.size(); i++) {
                List<AbstractTransaction> sy = sys.get(i);
                AbstractTransaction tx = sy.get(line);
                if (tx instanceof EntityHistoryTx) try{
                    EntityHistoryTx t = ((EntityHistoryTx) tx);
                    List<Pair<Integer, PVal>> r;
                    if(t.getResult()==null) r = Collections.emptyList();
                    else r = t.conv(((EntityHistoryTx.Result) t.getResult()).getHistory());
                    res.add(r);
                    if(i==0) System.out.print(t.getProp()+" "+t.getBeginTime()+","+t.getEndTime()+" ("+t.getEntity()+")  ");
                    System.out.print(dbList.get(i)+"("+r.size()+")    ");
                }catch (Exception e){
                    System.err.println("Error in "+dbList.get(i));
                    e.printStackTrace();
                    throw e;
                }
            }
            OptionalInt tmp = res.stream().mapToInt(List::size).min();
            if(tmp.isPresent()){
                int ssize = tmp.getAsInt();
                System.out.print("line "+(line + 1));
                if(!res.stream().allMatch(pairs -> pairs.size()==ssize)) System.out.println("  ------ X");
                else System.out.println();
//                for(int i=0; i< sys.size(); i++) System.out.print(fileNames.get(i)+"    ");
                boolean alllEq = true;
                StringBuilder sb = new StringBuilder();
                for(int k=0; k<ssize;k++){
                    boolean allEq = true;
                    for(int i=0; i< sys.size(); i++){
                        Pair<Integer, PVal> cur = res.get(i).get(k);
                        sb.append(cur.getKey()).append(" ").append(cur.getValue()).append("    ");
                        if(i>0){
                            Pair<Integer, PVal> pre = res.get(i - 1).get(k);
                            if(!(pre.getKey().equals(cur.getKey()) && eqv(pre.getValue(), cur.getValue()))) allEq=false;
                        }
                    }
                    if(!allEq) alllEq = false;
                    sb.append("   ").append(allEq ? "" : "X").append('\n');
                }
                if(!alllEq) System.out.println(sb);
            }
        }
    }

    private boolean eqv(Object pre, Object cur) {
        if(pre instanceof Float && cur instanceof Float){
            float diff = Math.abs(((Float)pre) - ((Float)cur));
            return diff < 0.01;
        }
        return Objects.equals(pre, cur);
    }

    private List<List<AbstractTransaction>> toList(List<BenchmarkReader> txResultReaders) throws IOException {
        List<List<AbstractTransaction>> res = new ArrayList<>();
        for(BenchmarkReader reader : txResultReaders) {
            res.add(txList(reader));
            reader.close();
        }
        return res;
    }

    private List<AbstractTransaction> txList(BenchmarkReader reader){
        List<AbstractTransaction> res = new ArrayList<>();
        while(reader.hasNext()) res.add(reader.next());
        return res;
    }

}
