package edu.buaa.common.transaction;

import edu.buaa.common.utils.PFieldList;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class UpdateTemporalDataTx extends AbstractTransaction {
    private PFieldList data = new PFieldList(); // start, end, eid, prop, value
    private boolean isNode;

    public UpdateTemporalDataTx() {
        this.setTxType(TxType.tx_update_temporal_data);
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
            Set<String> props = data.keysWithout("u_sid", "st", "et");
            for (int i = 0; i < data.size(); i++) {
                int s = data.get("st", i).i();
                int e = data.get("et", i).i();
                for (String prop : props) {
                    ArrayList<Pair<Integer, Integer>> temp = cacheForGetFineGrainedInfo.computeIfAbsent(prop, k -> new ArrayList<>());
                    temp.add(Pair.of(s, e));
                }
            }
        }
        return cacheForGetFineGrainedInfo;
    }

    private HashMap<String, HashMap<String, ArrayList<Pair<Integer, Integer>>>> cacheForGetFineGrainedInfoWithEntity = null;

    @Override
    protected HashMap<String, HashMap<String, ArrayList<Pair<Integer, Integer>>>> getFineGrainedInfoWithEntity() {
        if (cacheForGetFineGrainedInfoWithEntity == null) {
            cacheForGetFineGrainedInfoWithEntity = new HashMap<>();
            Set<String> props = data.keysWithout("u_sid", "st", "et");
            for (int i = 0; i < data.size(); i++) {
                String id = data.get("u_sid", i).s();
                int s = data.get("st", i).i();
                int e = data.get("et", i).i();
                for (String prop : props) {
                    HashMap<String, ArrayList<Pair<Integer, Integer>>> propTimeRangesMap = cacheForGetFineGrainedInfoWithEntity.computeIfAbsent(id, k -> new HashMap<>());
                    ArrayList<Pair<Integer, Integer>> temp = propTimeRangesMap.computeIfAbsent(prop, k -> new ArrayList<>());
                    temp.add(Pair.of(s, e));
                }
            }
        }
        return cacheForGetFineGrainedInfoWithEntity;
    }

    private HashSet<String> cacheForGetEntities = null;

    @Override
    protected HashSet<String> getEntities() {
        if (cacheForGetEntities == null) {
            cacheForGetEntities = new HashSet<>();
            for (int i = 0; i < data.size(); i++) {
                cacheForGetEntities.add(data.get("u_sid", i).s());
            }
        }
        return cacheForGetEntities;
    }

    public UpdateTemporalDataTx(PFieldList data, boolean isNode) {
        this.isNode = isNode;
        this.setTxType(TxType.tx_update_temporal_data);
        this.data = data;
    }

    public PFieldList getData() {
        return data;
    }

    public void setData(PFieldList data) {
        this.data = data;
    }

    public boolean isNode() {
        return isNode;
    }

    public void setNode(boolean node) {
        isNode = node;
    }
}