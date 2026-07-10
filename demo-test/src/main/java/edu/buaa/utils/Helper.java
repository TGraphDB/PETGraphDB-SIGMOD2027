package edu.buaa.utils;

import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.serializer.SimplePropertyPreFilter;
import com.aliyun.openservices.aliyun.log.producer.LogProducer;
import com.aliyun.openservices.aliyun.log.producer.Producer;
import com.aliyun.openservices.aliyun.log.producer.ProducerConfig;
import com.aliyun.openservices.aliyun.log.producer.ProjectConfig;
import com.aliyun.openservices.aliyun.log.producer.errors.ProducerException;
import com.csvreader.CsvReader;
import com.google.common.base.Preconditions;
import com.google.common.collect.PeekingIterator;
import edu.buaa.common.RuntimeEnv;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.neo4j.graphdb.temporal.TimePoint;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

import com.fundebug.Fundebug;

/**
 * Created by song on 16-2-23.
 */
public class Helper {
    {
        ParserConfig.getGlobalInstance().setAutoTypeSupport(true);
        serializerPropFilters.getExcludes().add("section");
    }
    public static SerializerFeature[] serializerFeatures = new SerializerFeature[] {
            SerializerFeature.WriteClassName,
            SerializerFeature.DisableCircularReferenceDetect,
//            SerializerFeature.PrettyFormat,
//            SerializerFeature.BeanToArray,
            SerializerFeature.QuoteFieldNames,
//            SerializerFeature.WriteMapNullValue,
    };
    public static SimplePropertyPreFilter serializerPropFilters = new SimplePropertyPreFilter();

    public static String codeGitVersion() {
        try (InputStream input = Helper.class.getResourceAsStream("/git.properties")) {
            if(input==null) return "NoGit";
            Properties prop = new Properties();
            prop.load(input);
            String gitCommitId = prop.getProperty("git.commit.id.describe-short");
            if(gitCommitId.endsWith("-Modified")){
                return gitCommitId.replace("-Modified", "(M)");
            }else{
                return gitCommitId;
            }
        } catch (IOException ex) {
            if(ex instanceof FileNotFoundException) return "NoGit";
            ex.printStackTrace();
            return "Git-Err";
        }
    }

    public static String getTestName(String benchmarkFileName, String dbName){
        Calendar c = Calendar.getInstance();
        return benchmarkFileName.replace("benchmark","B")+"@"+dbName+"^"+
                c.get(Calendar.YEAR)+"."+(c.get(Calendar.MONTH)+1)+"."+c.get(Calendar.DAY_OF_MONTH)+"_"+
                c.get(Calendar.HOUR_OF_DAY)+":"+c.get(Calendar.MINUTE);
    }

    public static String getTestName(int jenkinsId, String benchmarkFileName, String dbName, int maxConnCnt, int reqRate){
        return getTestName(benchmarkFileName, dbName)+"@J"+jenkinsId+"*"+maxConnCnt+"("+reqRate+")";
    }

    public static Producer getLogger(){
        ProducerConfig pConf = new ProducerConfig();
        pConf.setIoThreadCount( 10 ); // one thread to upload
        Producer onlineLogger = new LogProducer( pConf );
        onlineLogger.putProjectConfig(new ProjectConfig("tgraph-demo-test", "cn-beijing.log.aliyuncs.com", mustEnv("ALI_LOG_ID"), mustEnv("ALI_LOG_SECRET")));
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            try {
//                onlineLogger.close();
//            } catch (InterruptedException | ProducerException e) {
//                e.printStackTrace();
//            }
//        }));
        return onlineLogger;
    }


    private static final Fundebug fundebug = new Fundebug("e4cd12b922024c8de9ec8ca8e8e65d3f4070b5cd8090e24493ad2255fd7baed1");
    private static int errSeq = 0;
    private static long programBeginTime = System.currentTimeMillis();
    private static final Map<String, Object> m = new ConcurrentSkipListMap<>();
    public static Fundebug trace(){
        if(m.isEmpty()){
            initFunDebugMeta("JOB_NAME", "JENKINS_ID", "NODE_NAME", "DB_NAME", "MILESTONE_NAME", "BENCHMARK_NAME");
        }
        m.put("error_seq", errSeq++);
        m.put("time_elapse", System.currentTimeMillis() - programBeginTime);
        fundebug.setMetaData(m);
        return fundebug;
    }

    private static void initFunDebugMeta(String... envList){
        for(String env: envList){
            String v = envOrDefault(env, null);
            if(v!=null) m.put(env.toLowerCase(), v);
        }
        String beginT = envOrDefault("MARK_TIME", null);
        if(beginT!=null) {
            programBeginTime = Long.parseLong(beginT);
        }
        m.put("program_begin_time", programBeginTime);
    }

    public static void deleteAllFilesOfDir(File path) {
        if (!path.exists())
            return;
        if (path.isFile()) {
            path.delete();
            return;
        }
        File[] files = path.listFiles();
        for (int i = 0; i < files.length; i++) {
            deleteAllFilesOfDir(files[i]);
        }
        path.delete();
    }

    public static String getString(int size) {
        char[] chars = new char[size];
        Arrays.fill(chars, 'a');
        return new String(chars);
    }

    public static byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }

    public static void getFileRecursive(File dir,List<File> fileList, int level){
        if(dir.isDirectory()){
            for (File file : dir.listFiles()) {
                if(!file.isDirectory() && file.getName().startsWith("TJamData_201") && file.getName().endsWith(".csv")){
                    fileList.add(file);
                }
            }
            if(level>0) {
                for (File file : dir.listFiles()) {
                    if (file.isDirectory()) {
                        getFileRecursive(file, fileList, level - 1);
                    }
                }
            }
        }
    }

    public static int[] calcSplit(int nthPart, int totalPartCount, int size) {
        if(!(0<nthPart && nthPart <= totalPartCount && totalPartCount <= size)){
            throw new RuntimeException("bad argument");
        }
        int[] lengthList = new int[totalPartCount];
        int base = size / totalPartCount;
        int remain=size % totalPartCount;
        int start=0;
        int end=0;

//        System.out.println((base+1)+"["+remain+" times], "+base+"["+(totalPartCount-remain)+" times]");
        for(int i=0;i<totalPartCount;i++) {
            lengthList[i] = base;
            if (remain > 0) {
                remain--;
                lengthList[i]++;
            }
        }
        int j;
        for( j=0;j<nthPart-1;j++){
            start+=lengthList[j];
        }
        end=start+lengthList[j]-1;
        return new int[]{start,end};
    }

    public static int getFileTimeStamp(File file){
        return timeStr2int(file.getName().substring(9, 21));
    }

    public static void deleteExistDB(File dir){
        if (dir.exists()){
            Helper.deleteAllFilesOfDir(dir);
        }
        dir.mkdir();
    }

    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static int timeStr2int(String tStr){
        String yearStr = tStr.substring(0,4);
        String monthStr = tStr.substring(4,6);
        String dayStr = tStr.substring(6,8);
        String hourStr = tStr.substring(8,10);
        String minuteStr = tStr.substring(10, 12);
        //String secondStr = tStr.substring(12,14);
//        System.out.println(yearStr+" "+monthStr+" "+dayStr+" "+hourStr+" "+minuteStr);
        int year = Integer.parseInt(yearStr);
        int month = Integer.parseInt(monthStr)-1;//month count from 0 to 11, no 12
        int day = Integer.parseInt(dayStr);
        int hour = Integer.parseInt(hourStr);
        int minute = Integer.parseInt(minuteStr);
       // int second = Integer.parseInt(secondStr);
//        System.out.println(year+" "+month+" "+day+" "+hour+" "+minute);
        Calendar ca= Calendar.getInstance();
        ca.set(year, month, day, hour, minute, 0); //seconds set to 0
     //   ca.set(year, month, day, hour, minute, second); //seconds set to 0
        long timestamp = ca.getTimeInMillis();
//        System.out.println(timestamp);
        if(timestamp/1000<Integer.MAX_VALUE){
            return (int) (timestamp/1000);
        }else {
            throw new RuntimeException("timestamp larger than Integer.MAX_VALUE, this should not happen");
        }
    }

    public static String timeStamp2String(final int timestamp){
        Calendar ca= Calendar.getInstance();
        ca.setTimeInMillis(((long) timestamp) * 1000);
        return ca.get(Calendar.YEAR)+"-"+(ca.get(Calendar.MONTH)+1)+"-"+ca.get(Calendar.DAY_OF_MONTH)+" "+
                String.format("%02d", ca.get(Calendar.HOUR_OF_DAY))+":"+
                String.format("%02d", ca.get(Calendar.MINUTE));
    }

    public static <E> PeekingIterator<E> emptyIterator(){
        return new PeekingIterator<E>() {
            @Override public boolean hasNext() { return false; }
            @Override public E peek() { throw new RuntimeException("empty Iterator!"); }
            @Override public E next() { throw new RuntimeException("empty Iterator!"); }
            @Override public void remove() { throw new UnsupportedOperationException(); }
        };
    }

    private static final DateTimeFormatter format1 = DateTimeFormatter.ofPattern("yyyyMMdd");//  20120101
    public static int str2UnixTimestamp(String yyyyMMdd) {
        LocalDate parsedDate;
        parsedDate = LocalDate.parse(yyyyMMdd, format1);
        long timestamp = Date.from(parsedDate.atStartOfDay(ZoneOffset.ofHours(8)).toInstant()).getTime();
//            System.out.println(timestamp);
        return Math.toIntExact(timestamp / 1000);
    }

    public static String unixTimestamp2yyyyMMdd(Date t) {
        LocalDate parsedDate;
        parsedDate = LocalDate.of(t.getYear(), t.getMonth(), t.getDate());
        return parsedDate.format(format1);
    }

    public static File download( String url, File out ) throws IOException {
        if(out.exists() && out.isFile()){
            return out;
        }
        URL website = new URL(url);
        ReadableByteChannel rbc = Channels.newChannel(website.openStream());
        FileOutputStream fos = new FileOutputStream(out);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        return out;
    }

    public static File decompressGZip( File input, File outFile ) throws IOException {
        try (GzipCompressorInputStream in = new GzipCompressorInputStream(new FileInputStream(input))){
            IOUtils.copy(in, new FileOutputStream(outFile));
        }
        return outFile;
    }

    public static BufferedReader gzipReader(File file) throws IOException {
        return new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file))));
    }

    public static BufferedReader read(File file) throws IOException {
        try{
            return gzipReader(file);
        }catch (ZipException ignore){
            return new BufferedReader(new FileReader(file));
        }
    }

    public static TimePoint time(int timestamp){
        return new TimePoint(timestamp);
    }

    private static final Map<String, String> defaultEnv = new HashMap<>();
    public static void loadDefaultEnv(String env){
        String[] arr = env.split(";");
        for(String kv : arr){
            String[] kvArr = kv.split("=");
            defaultEnv.put(kvArr[0], kvArr[1]);
        }
    }

    public static String mustEnv(String name) {
        String val = System.getenv(name);
        if(val==null) val = defaultEnv.get(name);
        if(val==null) {
            val = System.getenv(name.toLowerCase());
            if(val==null) throw new NullPointerException(name);
        }
        return val;
    }

    public static String envOrDefault(String key, String defaultVal) {
        String s = System.getenv(key);
        if(s==null) {
            s = System.getenv(key.toLowerCase());
            if(s==null) return defaultVal;
        }
        return s;
    }

    public static <T> Triple<Set<T>, Set<T>, Set<T>> compareSets(Collection<T>correct_c, Collection<T> input_c){
        Set<T> correct = new HashSet<>(correct_c);
        Set<T> input = new HashSet<>(input_c);
        Set<T> common = new HashSet<>(input);
        common.retainAll(correct);
        correct.removeAll(common);
        input.removeAll(common);
        return Triple.of(correct, common, input);
    }

    public static <T extends Comparable<T>, V> boolean validateResult(Map<T, V> correct_c, Map<T, V> input_c){
        Triple<Set<T>, Set<T>, Set<T>> r = compareSets(correct_c.keySet(), input_c.keySet());
        List<T> correctDiff = new ArrayList<>(r.getLeft());
        List<T> inputDiff = new ArrayList<>(r.getRight());
        List<T> common = new ArrayList<>(r.getMiddle());
        common.sort(Comparator.naturalOrder());
        StringBuilder sbc = new StringBuilder();
        List<Triple<T, V, V>> inCorrect = new ArrayList<>();
        for(T key : common){
            V cVal = correct_c.get(key);
            V iVal = input_c.get(key);
            if(!Objects.equals(cVal, iVal)){
                inCorrect.add(Triple.of(key, cVal, iVal));
            }
        }
        correctDiff.sort(Comparator.naturalOrder());
        inputDiff.sort(Comparator.naturalOrder());
        if(!correctDiff.isEmpty() || !inputDiff.isEmpty() || !inCorrect.isEmpty()){
            StringBuilder sb = new StringBuilder();
            sb.append("-").append(correctDiff.size()).append(", ").append(r.getMiddle().size()).append(", +").append(inputDiff.size()).append(" [");
            int i=0;
            for(T p : correctDiff){
                sb.append(p).append(':').append(correct_c.get(p)).append(", ");
                if(++i>2) break;
            }
            sb.append('<');
            i=0;
            for(Triple<T, V, V> p : inCorrect){
                sb.append(p.getLeft()).append(':').append(p.getMiddle()).append('|').append(p.getRight()).append(", ");
                if(++i>2) break;
            }
            sb.append(">, ");
            i=0;
            for(T p : inputDiff){
                sb.append(p).append(':').append(input_c.get(p)).append(", ");
                if(++i>2) break;
            }
            sb.append(']');
//            System.out.println(sb);
//            return false;
            throw new SetNotMath(sb.toString(), correctDiff, inputDiff, r.getMiddle().size());
        }else {
            return true;
        }
    }

    public static <T extends Comparable<T>> boolean validateResult(Collection<T> correct_c, Collection<T> input_c){
        Triple<Set<T>, Set<T>, Set<T>> r = compareSets(correct_c, input_c);
        List<T> correctDiff = new ArrayList<>(r.getLeft());
        List<T> inputDiff = new ArrayList<>(r.getRight());
        correctDiff.sort(Comparator.naturalOrder());
        inputDiff.sort(Comparator.naturalOrder());
        if(!correctDiff.isEmpty() || !inputDiff.isEmpty()){
            StringBuilder sb = new StringBuilder();
            sb.append("-").append(correctDiff.size()).append(", ").append(r.getMiddle().size()).append(", +").append(inputDiff.size()).append(" [");
            int i=0;
            for(T p : correctDiff){
                sb.append(p).append(", ");
                if(++i>2) break;
            }
            sb.append(" | ");
            i=0;
            for(T p : inputDiff){
                sb.append(p).append(", ");
                if(++i>2) break;
            }
//            System.out.println(sb);
//            return false;
            throw new SetNotMath(sb.toString(), correctDiff, inputDiff, r.getMiddle().size());
        }else {
            return true;
        }
    }

    public static String fileLinuxPath(File file) {
        return file.getAbsolutePath().replace('\\','/');
    }

    public static Triple<String, String, String> extractMilestone(String milestoneName) {
        String[] arr = milestoneName.split("_");
        return Triple.of(arr[1], arr[2].toUpperCase(), "T."+arr[3]);
    }

    public static class SetNotMath extends RuntimeException{
        public final List<? extends Comparable> a;
        public final List<? extends Comparable> b;
        public final int common;

        public <T extends Comparable<T>> SetNotMath(String msg, List<T> a, List<T> b, int common) {
            super(msg);
            this.a = a;
            this.b = b;
            this.common = common;
        }
    }

    public static int getFileTime(File file){
        return Integer.parseInt(file.getName().substring(10, 21));
    }

    public static ArrayList<String[]> csvReader(String filePath){
        ArrayList<String[]> csvList = new ArrayList<>();
        try{
            CsvReader reader =new CsvReader(filePath,',', Charset.forName("GBK"));
            reader.readHeaders();
            while (reader.readRecord()){
                csvList.add(reader.getValues());
            }
            reader.close();
        }catch (Exception e){
            e.printStackTrace();
        }
        return csvList;
    }
}