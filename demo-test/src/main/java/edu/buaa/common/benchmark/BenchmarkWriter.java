package edu.buaa.common.benchmark;


import com.alibaba.fastjson.JSON;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.utils.Helper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 *
 */
public class BenchmarkWriter {

    public static void main(String[] args) {
        System.out.println("process benchmark and retain read tx only (no write data).");
        String benchmarkFileName = Helper.mustEnv("BENCHMARK_FILE_INPUT");
        String benchmarkOutputFileName = Helper.mustEnv("BENCHMARK_FILE_OUTPUT");
        try {
            BenchmarkReader reader = new BenchmarkReader(new File(benchmarkFileName));
            BenchmarkWriter writer = new BenchmarkWriter(new File(benchmarkOutputFileName));
            while (reader.hasNext()) {
                AbstractTransaction tx = reader.next();
                if(tx.getTxType().isReadTx()) writer.write(tx);
            }
            reader.close();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final OutputStream writer;

    public BenchmarkWriter(File file) throws IOException {
        writer = new GZIPOutputStream(new FileOutputStream(file));
    }

    public BenchmarkWriter(File file, boolean compress) throws IOException {
        if(compress){
            writer = new GZIPOutputStream(new FileOutputStream(file));
        }else {
            writer = new FileOutputStream(file);
        }
    }

    public void write(AbstractTransaction tx) throws IOException {
        writer.write(JSON.toJSONString(tx, Helper.serializerFeatures).getBytes());
        writer.write('\n');
    }

    public void write(List<AbstractTransaction> tx) throws IOException {
        for(AbstractTransaction t : tx){
            write(t);
        }
    }

    public void write(Iterator<AbstractTransaction> txIter) throws IOException {
        while(txIter.hasNext()) write(txIter.next());
    }

    public void close() throws IOException {
        writer.close();
    }
}
