package edu.buaa.client;


import com.alibaba.fastjson.JSON;
import com.google.common.util.concurrent.ListenableFuture;
import edu.buaa.common.client.AbstractNeoClient;
import edu.buaa.common.client.DBProxy;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.utils.Helper;

public class NeoTGraphExecutorClient extends AbstractNeoClient implements DBProxy {

    @Override
    public ListenableFuture<ServerResponse> execute(AbstractTransaction tx) throws Exception{
        return this.addQuery(JSON.toJSONString(tx, Helper.serializerFeatures), tx.getSection());
    }

}
