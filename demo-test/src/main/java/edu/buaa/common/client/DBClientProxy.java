package edu.buaa.common.client;

import edu.buaa.common.utils.TemporalGraphPropertySchema;

public interface DBClientProxy extends DBProxy{
    DBClientProxy init(String serverHost, int port, String dbName, int parallelCnt, TemporalGraphPropertySchema schema) throws Exception;
}
