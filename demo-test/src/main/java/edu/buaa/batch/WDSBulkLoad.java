package edu.buaa.batch;

import edu.buaa.common.utils.PVal;
import org.act.temporalProperty.meta.ValueContentType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.temporal.TimePoint;

public class WDSBulkLoad extends TGSBulkLoad {

    public WDSBulkLoad() throws Exception {
        super();
    }

    @Override
    public void importTemporal() throws Exception {
        initEntityTp();
        Thread.interrupted();
        System.out.println("DB shutdown.");
        Thread.sleep(20);
    }

    @Override
    protected String tPMeta(PVal.Type type) {
        return "tp.int:unknown";
    }

    @Override
    protected void initEntityTp(boolean isNode, Transaction tx, long eid, String prop, PVal.Type type){
        if(isNode){
            tx.getNodeById(eid).setTemporalProperty(prop, new TimePoint(0), tPMeta(type));
        }else{
            tx.getRelationshipById(eid).setTemporalProperty(prop, new TimePoint(0), tPMeta(type));
        }
    }

}
