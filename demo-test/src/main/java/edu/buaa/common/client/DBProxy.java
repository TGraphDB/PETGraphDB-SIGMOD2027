package edu.buaa.common.client;

import com.google.common.util.concurrent.ListenableFuture;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.transaction.AbstractTransaction.Metrics;
import edu.buaa.common.transaction.AbstractTransaction.Result;

import java.io.IOException;

/**
 * Created by song on 16-2-23.
 */
public interface DBProxy {
    // return server version.
    String testServerClientCompatibility() throws UnsupportedOperationException;

    ListenableFuture<ServerResponse> execute(AbstractTransaction tx) throws Exception;

    void close() throws IOException, InterruptedException;

    class ServerResponse{
        private Result result;
        private Metrics metrics;

        public Result getResult() {
            return result;
        }

        public void setResult(Result result) { this.result = result; }

        public Metrics getMetrics() {
            return metrics;
        }

        public void setMetrics(Metrics metrics) {
            this.metrics = metrics;
        }
    }
}
