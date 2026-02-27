package edu.buaa.common.transaction;


import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class SnapshotAggrMaxTx extends AbstractTransaction {
    private boolean isNode;
    private int t0;
    private int t1;
    private String p;

    public SnapshotAggrMaxTx() {
        this.setTxType(TxType.tx_query_snapshot_aggr_max);
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

    public boolean isNode() {
        return isNode;
    }

    public void setNode(boolean node) {
        isNode = node;
    }

    public static class Result extends AbstractTransaction.Result {
        PFieldList propMaxValue = new PFieldList();

        public PFieldList getPropMaxValue() {
            return propMaxValue;
        }

        public void setPropMaxValue(PFieldList propMaxValue) {
            this.propMaxValue = propMaxValue;
        }

        public List<Pair<String, PVal>> getPropMaxValueList() {
            List<Pair<String, PVal>> lst = new ArrayList<>();
            for(int i=0; i<propMaxValue.size(); i++){
                lst.add(Pair.of(propMaxValue.get("entity", i).s(), propMaxValue.get("value", i)));
            }
            return lst;
        }

        public void setPropMaxValue(List<Pair<String, PVal>> propMaxValue) {
            for(Pair<String, PVal> p : propMaxValue){
                this.propMaxValue.add("entity", p.getKey());
                this.propMaxValue.add("value", p.getValue());
            }
        }
    }

    @Override
    public boolean validateResult(AbstractTransaction.Result result) {
        return Helper.validateResult(conv(((Result) this.getResult()).getPropMaxValueList()), conv(((Result) result).getPropMaxValueList()));
    }

    @Override
    protected boolean infoIsNode() {
        return isNode;
    }

    private HashMap<String, ArrayList<org.apache.commons.lang3.tuple.Pair<Integer, Integer>>> cacheForGetFineGrainedInfo = null;

    @Override
    protected HashMap<String, ArrayList<org.apache.commons.lang3.tuple.Pair<Integer, Integer>>> getFineGrainedInfo() {
        if (cacheForGetFineGrainedInfo == null) {
            cacheForGetFineGrainedInfo = new HashMap<>();
            ArrayList<org.apache.commons.lang3.tuple.Pair<Integer, Integer>> temp = new ArrayList<>();
            temp.add(org.apache.commons.lang3.tuple.Pair.of(t0, t1));
            cacheForGetFineGrainedInfo.put(p, temp);
        }
        return cacheForGetFineGrainedInfo;
    }

    @Override
    protected HashMap<String, HashMap<String, ArrayList<org.apache.commons.lang3.tuple.Pair<Integer, Integer>>>> getFineGrainedInfoWithEntity() {
        return null;
    }

    @Override
    protected HashSet<String> getEntities() {
        return null;
    }

    private Map<String, PVal> conv(List<Pair<String, PVal>> from){
        Map<String, PVal> r = from.stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        if(r.size()< from.size()) System.out.println("WARNING: duplicate entries in result.");
        return r;
    }
}
