package edu.buaa.common.transaction;

import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class SnapshotQueryTx extends AbstractTransaction {

    private boolean isNode = false;

    private int timestamp;

    private String propertyName;

    public SnapshotQueryTx() {
        this.setTxType(TxType.tx_query_snapshot);
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public boolean isNode() {
        return isNode;
    }

    public void setNode(boolean node) {
        isNode = node;
    }

    public static class Result extends AbstractTransaction.Result {
        PFieldList status = new PFieldList();

        public PFieldList getStatus() {
            return status;
        }

        public void setStatus(PFieldList status) {
            this.status = status;
        }

        public void answer(List<Pair<String, PVal>> status) {
            for(Pair<String, PVal> p : status){
                this.status.add("entity", p.getKey());
                this.status.add("value", p.getValue());
            }
        }
    }

    public static List<Pair<String, PVal>> toStatusList(PFieldList status) {
        List<Pair<String, PVal>> lst = new ArrayList<>();
        for(int i=0; i<status.size(); i++){
            lst.add(Pair.of(status.get("entity", i).s(), status.get("value", i)));
        }
        return lst;
    }

    @Override
    public boolean validateResult(AbstractTransaction.Result result) {
        return Helper.validateResult(conv(((Result) this.getResult()).getStatus()), conv(((Result) result).getStatus()));
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
            temp.add(org.apache.commons.lang3.tuple.Pair.of(timestamp, timestamp));
            cacheForGetFineGrainedInfo.put(propertyName, temp);
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

    private Map<String, PVal> conv(PFieldList status){
        List<Pair<String, PVal>> from = toStatusList(status);
        Map<String, PVal> r = from.stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        if(r.size()< from.size()) System.out.println("WARNING: duplicate entries in result.");
        return r;
    }
}
