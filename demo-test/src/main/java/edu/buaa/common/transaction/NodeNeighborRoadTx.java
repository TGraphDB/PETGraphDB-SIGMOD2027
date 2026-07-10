package edu.buaa.common.transaction;

import edu.buaa.utils.Helper;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class NodeNeighborRoadTx extends AbstractTransaction {

    private long nodeId;

    public NodeNeighborRoadTx(long nodeId, List<String> roadIds) {
        this.setTxType(TxType.tx_query_node_neighbor_road);
        this.nodeId = nodeId;
        Result r = new Result();
        r.setRoadIds(roadIds);
        this.setResult(r);
    }

    public NodeNeighborRoadTx(){}

    public long getNodeId() {
        return nodeId;
    }

    public void setNodeId(long nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public boolean validateResult(AbstractTransaction.Result result) {
        return Helper.validateResult(((Result) this.getResult()).getRoadIds(), ((Result) result).getRoadIds());
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

    public static class Result extends AbstractTransaction.Result{
        List<String> roadIds;

        public List<String> getRoadIds() {
            return roadIds;
        }

        public void setRoadIds(List<String> roadIds) {
            this.roadIds = roadIds;
        }
    }
}
