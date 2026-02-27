package edu.buaa.batch;

import edu.buaa.common.benchmark.NeoMilestoneBuilder;

import java.io.File;
import java.io.IOException;

public class EmptyServerBulkLoad extends NeoMilestoneBuilder {


    public EmptyServerBulkLoad() throws Exception { }

    @Override
    public void importStatic() throws Exception {
        System.out.println("import static data");
    }

    @Override
    public void importTemporal() throws Exception {
        System.out.println("import temporal data");
    }

    @Override
    public void close() throws Exception {

    }

    @Override
    protected void init(File dbPath) throws IOException {

    }
}
