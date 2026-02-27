package edu.buaa.client;

import com.google.common.util.concurrent.ListenableFuture;
import edu.buaa.common.client.AbstractNeoClient;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.utils.TimeMonitor;

import java.util.Set;
import java.util.stream.Collectors;

import static java.text.MessageFormat.format;

public class TCypherExecutorClient extends AbstractNeoClient{

//    @Override
//    protected ServerResponse onResponse(String query, String response, TimeMonitor timeMonitor, Thread thread) throws Exception {
//        return null;
//    }

    @Override
    public ListenableFuture<ServerResponse> execute(AbstractTransaction tx) throws Exception {
        switch (tx.getTxType()){
//            case tx_query_entity_history: return execute((EntityHistoryTx) tx);
            case tx_import_temporal_data: return execute((ImportTemporalDataTx) tx);
            case tx_update_temporal_data: return execute((UpdateTemporalDataTx) tx);
            case tx_query_snapshot_aggr_max: return execute((SnapshotAggrMaxTx) tx);
            case tx_query_snapshot_aggr_duration: return execute((SnapshotAggrDurationTx) tx);
            case tx_query_snapshot: return execute((SnapshotQueryTx) tx);
            case tx_query_road_by_temporal_condition: return execute((EntityTemporalConditionTx) tx);
            default:
                throw new UnsupportedOperationException();
        }
    }

    private ListenableFuture<ServerResponse> execute(UpdateTemporalDataTx tx) throws InterruptedException {
        StringBuilder sb = new StringBuilder();
        PFieldList data = tx.getData();
        int size = data.size();
        Set<String> props = data.keysWithout("u_sid", "st", "et");
        for (int i=0; i<size; i++) {
            sb.append("MATCH ").append(tx.isNode()?"(e)":"()-[e:EDGE_TO]->()")
                    .append(" WHERE e.u_sid='").append(data.get("u_sid", i).s()).append("' SET ");
            int finalI = i;
            sb.append(props.stream().map(key -> format("e.{0}=TV({1,number,#}~{2,number,#}:{3,number,#})",
                    key,
                    data.get("st", finalI).i(),
                    data.get("et", finalI).i(),
                    data.get(key, finalI).getVal())).collect(Collectors.joining(",")));
            sb.append(';');
        }
        return addQuery(sb.toString(), tx.getSection());
    }

    private ListenableFuture<ServerResponse> execute(ImportTemporalDataTx tx) throws InterruptedException {
        StringBuilder sb = new StringBuilder();
        PFieldList data = tx.getData();
        int size = data.size();
        Set<String> props = data.keysWithout("u_sid", "t");
        for (int i=0; i<size; i++) {
            sb.append("MATCH ").append(tx.isNode()?"(e)":"()-[e:EDGE_TO]->()")
                    .append(" WHERE e.u_sid='").append(data.get("u_sid", i).s()).append("' SET ");
            int finalI = i;
            sb.append(props.stream().map(key -> format("e.{0}=TV({1,number,#}~NOW:{2})",
                    key,
                    data.get("t", finalI).i(),
                    data.get(key, finalI).getVal().toString())).collect(Collectors.joining(",")));
            sb.append(';');
        }
        return addQuery(sb.toString(), tx.getSection());
    }

    private ListenableFuture<ServerResponse> execute(EntityHistoryTx tx) throws InterruptedException {
        return addQuery(format("MATCH {0} WHERE e.u_sid='{1}' RETURN e.u_sid, tp_value(e.{1}, {2,number,#}, {3,number,#})",
                tx.isNode()?"(e)":"()-[e:EDGE_TO]->()",
                tx.getEntity(), tx.getBeginTime(), tx.getEndTime()));
    }

    private ListenableFuture<ServerResponse> execute(SnapshotAggrMaxTx tx) throws InterruptedException {
        return addQuery(format("MATCH {0} RETURN e.u_sid, tp_max(e.{1}, {2,number,#}, {3,number,#})",
                tx.isNode()?"(e)":"()-[e:EDGE_TO]->()",
                tx.getP(), tx.getT0(), tx.getT1()));
    }

    private ListenableFuture<ServerResponse> execute(SnapshotAggrDurationTx tx) throws InterruptedException {
        return addQuery(format("MATCH {0} RETURN e.u_sid, tp_duration(e.{1}, {2,number,#}, {3,number,#})",
                tx.isNode()?"(e)":"()-[e:EDGE_TO]->()",
                tx.getP(), tx.getT0(), tx.getT1()));
    }

    private ListenableFuture<ServerResponse> execute(SnapshotQueryTx tx) throws InterruptedException {
        return addQuery(format("MATCH {0} RETURN e.u_sid, tp_value_at(e.{1}, {2,number,#})",
                tx.isNode()?"(e)":"()-[e:EDGE_TO]->()",
                tx.getPropertyName(), tx.getTimestamp()));
    }

    private ListenableFuture<ServerResponse> execute(EntityTemporalConditionTx tx) throws InterruptedException {
        return addQuery(format("MATCH {0} WHERE tp_within_exists(e.{1}, {2,number,#}, {3,number,#}, {4}, {5}) RETURN e.u_sid",
                tx.isNode()?"(e)":"()-[e:EDGE_TO]->()",
                tx.getP(), tx.getT0(), tx.getT1(), tx.getVMin().getVal().toString(), tx.getVMax().getVal().toString()));
    }

    public static class Result extends AbstractTransaction.Result{
        PFieldList results = new PFieldList();

        public PFieldList getResults() {
            return results;
        }

        public void setResults(PFieldList results) {
            this.results = results;
        }
    }
}
