package edu.buaa.common.transaction;

import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class CreateTGraphAggrMaxIndexTx extends AbstractTransaction {
    private int start, end;
    private String proName;
    private int every, timeUnit;

    public CreateTGraphAggrMaxIndexTx(){
        setTxType(TxType.tx_index_tgraph_aggr_max);
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public String getProName() {
        return proName;
    }

    public void setProName(String proName) {
        this.proName = proName;
    }

    public int getEvery() {
        return every;
    }

    public void setEvery(int every) {
        this.every = every;
    }

    public int getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(int timeUnit) {
        this.timeUnit = timeUnit;
    }

    public static class Result extends AbstractTransaction.Result{
        private long indexId;

        public long getIndexId() {
            return indexId;
        }

        public void setIndexId(long indexId) {
            this.indexId = indexId;
        }
    }

    @Override
    public boolean validateResult(AbstractTransaction.Result result) {
        Result r = (Result) result;
        System.out.println("create tgraph min/max index with id: "+r.indexId);
        return true;
    }

    @Override
    protected boolean infoIsNode() {
        return false;
    }

    @Override
    protected HashMap<String, ArrayList<Pair<Integer, Integer>>> getFineGrainedInfo() {
        return null;
    }

    @Override
    protected HashMap<String, HashMap<String, ArrayList<Pair<Integer, Integer>>>> getFineGrainedInfoWithEntity() {
        return null;
    }

    @Override
    protected HashSet<String> getEntities() {
        return null;
    }
}
