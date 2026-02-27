package edu.buaa.common.transaction;

import edu.buaa.common.utils.PFieldList;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class ImportTemporalDataTx extends AbstractTransaction {
    public PFieldList data = new PFieldList();
    private boolean isNode;

    public ImportTemporalDataTx() {
        this.setTxType(TxType.tx_import_temporal_data);
    }

    @Override
    protected boolean infoIsNode() {
        return isNode;
    }

    private HashMap<String, ArrayList<Pair<Integer, Integer>>> cacheForGetFineGrainedInfo = null;

    @Override
    protected HashMap<String, ArrayList<Pair<Integer, Integer>>> getFineGrainedInfo() {
        if (cacheForGetFineGrainedInfo == null) {
            int maxTime = Integer.MAX_VALUE;
            cacheForGetFineGrainedInfo = new HashMap<>();
            Set<String> props = data.keysWithout("u_sid", "t");
            int minTime = Integer.MAX_VALUE;
            for (int i = 0; i < data.size(); i++) {
                int time = data.get("t", i).i();
                if (minTime > time) minTime = time;
            }
            for (String prop : props) {
                ArrayList<Pair<Integer, Integer>> temp = cacheForGetFineGrainedInfo.computeIfAbsent(prop, k -> new ArrayList<>());
                temp.add(Pair.of(minTime, maxTime));
            }
        }
        return cacheForGetFineGrainedInfo;
    }

    private HashMap<String, HashMap<String, ArrayList<Pair<Integer, Integer>>>> cacheForGetFineGrainedInfoWithEntity = null;

    @Override
    protected HashMap<String, HashMap<String, ArrayList<Pair<Integer, Integer>>>> getFineGrainedInfoWithEntity() {
        if (cacheForGetFineGrainedInfoWithEntity == null) {
            int maxTime = Integer.MAX_VALUE;
            cacheForGetFineGrainedInfoWithEntity = new HashMap<>();
            Set<String> props = data.keysWithout("u_sid", "t");
            for (int i = 0; i < data.size(); i++) {
                int time = data.get("t", i).i();
                String id = data.get("u_sid", i).s();
                for (String prop : props) {
                    HashMap<String, ArrayList<Pair<Integer, Integer>>> propTimeRangesMap = cacheForGetFineGrainedInfoWithEntity.computeIfAbsent(id, k -> new HashMap<>());
                    ArrayList<Pair<Integer, Integer>> temp = propTimeRangesMap.computeIfAbsent(prop, k -> new ArrayList<>());
                    temp.add(Pair.of(time, maxTime));
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
    // default constructor and getter setter are needed by json encode/decode.

    public ImportTemporalDataTx(PFieldList lines, boolean isNode) {
        this.setTxType(TxType.tx_import_temporal_data);
        this.isNode = isNode;
        this.data = lines;
        Metrics m = new Metrics();
        m.setReqSize(lines.size());
        this.setMetrics(m);
    }

    public boolean isNode() {
        return isNode;
    }

    public void setNode(boolean node) {
        isNode = node;
    }

    public PFieldList getData() {
        return data;
    }

    public void setData(PFieldList data) {
        this.data = data;
    }

}
