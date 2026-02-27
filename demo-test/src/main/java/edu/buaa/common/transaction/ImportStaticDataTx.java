package edu.buaa.common.transaction;

import edu.buaa.common.utils.PFieldList;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ImportStaticDataTx extends AbstractTransaction {
    private PFieldList nodes;
    private PFieldList rels;

    public ImportStaticDataTx() {
        this.setTxType(TxType.tx_import_static_data);
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

    public PFieldList getNodes() {
        return nodes;
    }

    public void setNodes(PFieldList nodes) {
        this.nodes = nodes;
    }

    public PFieldList getRels() {
        return rels;
    }

    public void setRels(PFieldList rels) {
        this.rels = rels;
    }


}
