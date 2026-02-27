package edu.buaa.batch;

import org.act.temporalProperty.impl.CompressionType;
import org.act.temporalProperty.impl.Options;

public class TGSBulkLoad extends TGBulkLoad{
    public TGSBulkLoad() throws Exception {
        Options.setCTP(CompressionType.SNAPPY);
    }
}
