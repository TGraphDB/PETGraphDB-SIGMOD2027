package edu.buaa.common.transaction;

import com.google.common.collect.PeekingIterator;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.*;

import java.util.*;

public class EntityHistoryTx extends AbstractTransaction {
    private boolean isNode;
    private String entity;
    private int beginTime;
    private int endTime;
    private String prop;

    public EntityHistoryTx(String entity, int beginTime, int endTime, String prop) {
        this.setTxType(TxType.tx_query_entity_history);
        this.entity = entity;
        this.beginTime = beginTime;
        this.endTime = endTime;
        this.prop = prop;
    }

    public EntityHistoryTx() {
        this.setTxType(TxType.tx_query_entity_history);
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public int getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(int beginTime) {
        this.beginTime = beginTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public void setEndTime(int endTime) {
        this.endTime = endTime;
    }

    public String getProp() {
        return prop;
    }

    public void setProp(String prop) {
        this.prop = prop;
    }

    public boolean isNode() {
        return isNode;
    }

    public void setNode(boolean node) {
        isNode = node;
    }

    public static class Result extends AbstractTransaction.Result {
        List<Triple<Integer, Integer, PVal>> history;

        public List<Triple<Integer, Integer, PVal>> getHistory() {
            return history;
        }

        public void setHistory(List<Triple<Integer, Integer, PVal>> history) {
            this.history = history;
        }
    }

    @Override
    public boolean validateResult(AbstractTransaction.Result result) {
        return Helper.validateResult(conv(((Result) this.getResult()).getHistory()), conv(((Result) result).getHistory()));
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
            temp.add(org.apache.commons.lang3.tuple.Pair.of(beginTime, endTime));
            cacheForGetFineGrainedInfo.put(prop, temp);
        }
        return cacheForGetFineGrainedInfo;
    }

    private HashMap<String, HashMap<String, ArrayList<org.apache.commons.lang3.tuple.Pair<Integer, Integer>>>> cacheForGetFineGrainedInfoWithEntity = null;

    @Override
    protected HashMap<String, HashMap<String, ArrayList<org.apache.commons.lang3.tuple.Pair<Integer, Integer>>>> getFineGrainedInfoWithEntity() {
        if (cacheForGetFineGrainedInfoWithEntity == null) {
            cacheForGetFineGrainedInfoWithEntity = new HashMap<>();
            cacheForGetFineGrainedInfoWithEntity.put(entity, getFineGrainedInfo());
        }
        return cacheForGetFineGrainedInfoWithEntity;
    }

    private HashSet<String> cacheForGetEntities = null;

    @Override
    protected HashSet<String> getEntities() {
        if (cacheForGetEntities == null) {
            cacheForGetEntities = new HashSet<>();
            cacheForGetEntities.add(entity);
        }
        return cacheForGetEntities;
    }

    public List<Pair<Integer, PVal>> conv(List<Triple<Integer, Integer, PVal>> from){
        TemporalValue<PVal> tv = new TemporalValue<>();
        for(Triple<Integer, Integer, PVal> f : from){
            tv.set(new TimePointInt(f.getLeft()), new TimePointInt(f.getMiddle()), f.getRight());
        }
        tv.mergeSameVal();
        PeekingIterator<Map.Entry<TimePointInt, PVal>> it = tv.pointEntries();
        List<Pair<Integer, PVal>> res = new ArrayList<>();
        while(it.hasNext()){
            Map.Entry<TimePointInt, PVal> p = it.next();
            res.add(Pair.of(p.getKey().val(), p.getValue()));
        }
        return res;
    }
}
