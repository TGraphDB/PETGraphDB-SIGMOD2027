package edu.buaa.common.transaction;

import com.alibaba.fastjson.annotation.JSONField;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Triple;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class SnapshotAggrDurationTx extends AbstractTransaction {
    private boolean isNode;
    private int t0;
    private int t1;
    private String p;
    private List<PVal> intervalStarts;

    public SnapshotAggrDurationTx() {
        this.setTxType(TxType.tx_query_snapshot_aggr_duration);
    }

    public int getT0() {
        return t0;
    }

    public int getT1() {
        return t1;
    }

    public String getP() {
        return p;
    }

    public void setT0(int t0) {
        this.t0 = t0;
    }

    public void setT1(int t1) {
        this.t1 = t1;
    }

    public void setP(String p) {
        this.p = p;
    }

    @JSONField(serialize = false)
    public TreeSet<PVal> getIntStartTreeSet() {
        return new TreeSet<>(intervalStarts);
    }

    public List<PVal> getIntervalStarts() {
        return intervalStarts;
    }

    public void setIntervalStarts(List<PVal> intervalStarts) {
        this.intervalStarts = intervalStarts;
    }

    public boolean isNode() {
        return isNode;
    }

    public void setNode(boolean node) {
        isNode = node;
    }

    public static class Result extends AbstractTransaction.Result {
        PFieldList statusDuration = new PFieldList();

        public PFieldList getStatusDuration() {
            return statusDuration;
        }

        public void setStatusDuration(PFieldList statusDuration) {
            this.statusDuration = statusDuration;
        }

        public List<Triple<String, PVal, Integer>> getStatusDurationTripleList() {
            List<Triple<String, PVal, Integer>> lst = new ArrayList<>();
            for(int i=0; i<statusDuration.size(); i++){
                lst.add(Triple.of(
                        statusDuration.get("entity", i).s(),
                        statusDuration.get("value", i),
                        statusDuration.get("duration", i).i()));
            }
            return lst;
        }

        public void setStatusDuration(List<Triple<String, PVal, Integer>> statusDuration) {
            for(Triple<String, PVal, Integer> p : statusDuration){
                this.statusDuration.add("entity", p.getLeft());
                this.statusDuration.add("value", p.getMiddle());
                this.statusDuration.add("duration", p.getRight());
            }
        }
    }

    @Override
    public boolean validateResult(AbstractTransaction.Result result) {
        return Helper.validateResult(conv(((Result) this.getResult()).getStatusDurationTripleList()), conv(((Result) result).getStatusDurationTripleList()));
    }

    @Override
    protected boolean infoIsNode() {
        return isNode;
    }

    private HashMap<String, ArrayList<Pair<Integer, Integer>>> cacheForGetFineGrainedInfo = null;

    @Override
    protected HashMap<String, ArrayList<Pair<Integer, Integer>>> getFineGrainedInfo() {
        if (cacheForGetFineGrainedInfo == null) {
            cacheForGetFineGrainedInfo = new HashMap<>();
            ArrayList<Pair<Integer, Integer>> temp = new ArrayList<>();
            temp.add(Pair.of(t0, t1));
            cacheForGetFineGrainedInfo.put(p, temp);
        }
        return cacheForGetFineGrainedInfo;
    }

    @Override
    protected HashMap<String, HashMap<String, ArrayList<Pair<Integer, Integer>>>> getFineGrainedInfoWithEntity() {
        return null;
    }

    @Override
    protected HashSet<String> getEntities() {
        return null;
    }

    private List<Triple<String, String, Integer>> conv(List<Triple<String, PVal, Integer>> from){
        return from.stream().map(t->Triple.of(t.getLeft(), t.getMiddle().toString(), t.getRight())).collect(Collectors.toList());
    }
}
