package edu.buaa.common.benchmark;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.google.common.collect.AbstractIterator;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.utils.Helper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.zip.ZipException;

public class BenchmarkReader extends AbstractIterator<AbstractTransaction> {
    public final File file;
    private final BufferedReader reader;

    public BenchmarkReader(File file) throws IOException {
        this.file = file;
        reader = Helper.gzipReader(file);
    }

    public BenchmarkReader(File file, boolean isCompressed) throws IOException {
        this.file = file;
        BufferedReader reader;
        if(isCompressed) {
            try {
                reader = Helper.gzipReader(file);
            }catch (ZipException ignore){
                reader = new BufferedReader(new FileReader(file));
            }
        }else{
            reader = new BufferedReader(new FileReader(file));
        }
        this.reader = reader;
    }

    @Override
    protected AbstractTransaction computeNext() {
        try {
            String line = reader.readLine();
            if(line==null) return endOfData();
            return JSONObject.parseObject(line, AbstractTransaction.class, Feature.SupportAutoType);
        } catch (IOException e) {
            e.printStackTrace();
            return endOfData();
        }
    }

    public void close() throws IOException {
        reader.close();
    }
}
