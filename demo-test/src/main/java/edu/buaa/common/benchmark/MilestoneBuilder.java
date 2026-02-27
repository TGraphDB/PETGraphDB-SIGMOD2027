package edu.buaa.common.benchmark;


import edu.buaa.batch.TGBulkLoad;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.utils.Helper;
import edu.buaa.utils.TimeTicker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Calendar;

public abstract class MilestoneBuilder {
    protected Logger log;
    protected final TimeTicker ticker = new TimeTicker(10, 2);
    protected final TemporalGraphDataGenerator dataGen;
    protected final TemporalGraphPropertySchema schema;

    protected static final String clientClz = Helper.mustEnv("CLASS_DATA_IMPORT");

    protected final String dataGenClz = Helper.mustEnv("CLASS_DATA_GEN");
    protected final String dataset = Helper.mustEnv("DATASET");
    protected final String dataPath = Helper.mustEnv("DATA_PATH");
    protected final String dataSize = Helper.mustEnv("DATA_SIZE");
    protected final boolean staticOnly;

    protected String startTime;
    protected String endTime;

    public MilestoneBuilder() throws Exception {
        this.staticOnly = dataSize.equals("T_0");
        if(!staticOnly) {
            String tpRange = Helper.mustEnv(dataSize);
            startTime = tpRange.split("~")[0];
            endTime = tpRange.split("~")[1];
            System.out.println("build time set to "+ startTime +" ~ "+ endTime+" ("+dataSize+")");
        }else{
            System.out.println("build static only.");
        }
        this.schema = TemporalGraphPropertySchema.load(dataset);
        this.dataGen = txGenerator(dataGenClz, dataset, schema);
    }

    public static void main(String[] args){
        boolean forceExit = false;
        try {
            MilestoneBuilder mb = getDbBulkLoad(clientClz);
            forceExit = (mb instanceof TGBulkLoad);
            mb.importStatic();
            if(!mb.staticOnly) mb.importTemporal();
            mb.close();
        }catch (Exception e){
            Helper.trace().notifyError(e);
            throw new IllegalStateException(e);
        }
        finally {
            if (forceExit) System.exit(0);
        }
    }

    public abstract void importStatic() throws Exception;
    public abstract void importTemporal() throws Exception;
    public abstract void close() throws Exception;

    private TemporalGraphDataGenerator txGenerator(String dataGenClz, String dataset, TemporalGraphPropertySchema schema) throws Exception{
        Class<?> cls = Class.forName(dataGenClz);
        Object obj = cls.newInstance();
        TemporalGraphDataGenerator dg = (TemporalGraphDataGenerator) obj;
        return dg.init(new File(dataPath, dataset), schema);
    }

    public static MilestoneBuilder getDbBulkLoad(String clsClient) throws Exception {
        Class<?> cls = Class.forName(clsClient);
        Object obj = cls.newInstance();
        MilestoneBuilder client = (MilestoneBuilder) obj;
        client.log = LoggerFactory.getLogger(cls);
        return client;
    }

    public static String getTestName(String dbType){
        Calendar c = Calendar.getInstance();
        return "M_"+c.get(Calendar.YEAR)+"."+(c.get(Calendar.MONTH)+1)+"."+c.get(Calendar.DAY_OF_MONTH)+"_"+
                c.get(Calendar.HOUR_OF_DAY)+":"+c.get(Calendar.MINUTE)+dbType.toLowerCase();
    }

}
