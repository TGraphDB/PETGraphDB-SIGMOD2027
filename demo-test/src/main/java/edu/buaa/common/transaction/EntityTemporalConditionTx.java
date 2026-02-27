package edu.buaa.common.transaction;

import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class EntityTemporalConditionTx extends AbstractTransaction {
    private boolean isNode;
    private int t0;
    private int t1;
    private String p;
    private PVal vMin;
    private PVal vMax;

    public EntityTemporalConditionTx() {
        this.setTxType(TxType.tx_query_road_by_temporal_condition);
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

    public PVal getVMin() {
        return vMin;
    }

    public PVal getVMax() {
        return vMax;
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

    public void setVMin(PVal vMin) {
        this.vMin = vMin;
    }

    public void setVMax(PVal vMax) {
        this.vMax = vMax;
    }

    public boolean isNode() {
        return isNode;
    }

    public void setNode(boolean node) {
        isNode = node;
    }

    public static class Result extends AbstractTransaction.Result {
        List<String> entities;

        public List<String> getEntities() {
            return entities;
        }

        public void setEntities(List<String> entities) {
            this.entities = entities;
        }
    }

    @Override
    public boolean validateResult(AbstractTransaction.Result result) {
        return Helper.validateResult(((Result) this.getResult()).getEntities(), ((Result) result).getEntities());
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
}
