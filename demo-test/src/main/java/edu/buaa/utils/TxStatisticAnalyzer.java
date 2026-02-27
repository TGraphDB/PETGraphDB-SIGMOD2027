package edu.buaa.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * 用于分析一次run中所有一类事务的执行情况，绘制的结果图为折线图，按照执行总时间从小到大排序，总时间为一条折线，另外包含其他计算时间的折线（例如不包含用户代码的执行时间）
 */
public class TxStatisticAnalyzer {
    
    // 用于从一个jsonObject中获取事务执行的总时间
    private long getTotalTime(JSONObject object) {
        return object.getLong("duration");
    }

    private void draw(File outputFile, ArrayList<ArrayList<Long>> calculatedTime, String chartTitle, ArrayList<String> lineNames) throws IOException {
        // 将第0列作为排序依据和展示依据，是calculatedTime中的基本序列，一般是总执行时间，后续可能会部分替换为系统执行时间
        calculatedTime.sort(((o1, o2) -> {
            long cmp = o1.get(0) - o2.get(0);
            if (cmp > 0) return 1;
            if (cmp < 0) return -1;
            return 0;
        }));

        // 使用JFreeChart生成折线图
        // 需要依赖: org.jfree:jfreechart
        // 横坐标为txTimeList的下标，纵坐标为每个ArrayList<Long>中的元素
        int txCount = calculatedTime.size();
        if (txCount == 0) return;
        int lineCount = calculatedTime.get(0).size();

        // 原始全量数据chart
        org.jfree.data.xy.XYSeriesCollection dataset = new org.jfree.data.xy.XYSeriesCollection();
        for (int i = 0; i < lineCount; i++) {
            org.jfree.data.xy.XYSeries series = new org.jfree.data.xy.XYSeries(lineNames.get(i));
            for (int j = 0; j < txCount; j++) {
                ArrayList<Long> txTimes = calculatedTime.get(j);
                if (i < txTimes.size()) {
                    series.add(j, txTimes.get(i));
                }
            }
            dataset.addSeries(series);
        }

        org.jfree.chart.JFreeChart chart = org.jfree.chart.ChartFactory.createXYLineChart(
                chartTitle, // chart title
                "", // x axis label
                "Time (ns)", // y axis label
                dataset,
                org.jfree.chart.plot.PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        // 新增：前90%数据chart
        int x0 = (int) (txCount * 0.9);
        if (x0 < 1) x0 = 1;
        org.jfree.data.xy.XYSeriesCollection dataset90 = new org.jfree.data.xy.XYSeriesCollection();
        for (int i = 0; i < lineCount; i++) {
            org.jfree.data.xy.XYSeries series = new org.jfree.data.xy.XYSeries(lineNames.get(i));
            for (int j = 0; j < x0; j++) {
                ArrayList<Long> txTimes = calculatedTime.get(j);
                if (i < txTimes.size()) {
                    series.add(j, txTimes.get(i));
                }
            }
            dataset90.addSeries(series);
        }
        org.jfree.chart.JFreeChart chart90 = org.jfree.chart.ChartFactory.createXYLineChart(
                chartTitle + " (前90%)",
                "",
                "Time (ns)",
                dataset90,
                org.jfree.chart.plot.PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        int width = 1200;
        int height = 800;

        // 添加竖线 x = x0, x0为txCount的90%（向下取整）
        org.jfree.chart.plot.XYPlot plot = chart.getXYPlot();
        org.jfree.chart.plot.ValueMarker xMarker = new org.jfree.chart.plot.ValueMarker(x0);
        xMarker.setPaint(java.awt.Color.BLACK);
        xMarker.setStroke(new java.awt.BasicStroke(2.0f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_BEVEL, 0, new float[]{8.0f, 8.0f}, 0));
        xMarker.setLabel(String.format("x=%d", x0));
        xMarker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
        xMarker.setLabelTextAnchor(TextAnchor.BOTTOM_RIGHT);
        plot.addDomainMarker(xMarker);

        // 添加y=calculatedTime.get(x0).get(mainSeries)的黑色虚线
        if (x0 >= 0 && x0 < calculatedTime.size() && !calculatedTime.get(x0).isEmpty()) {
            double y0 = calculatedTime.get(x0).get(0);
            org.jfree.chart.plot.ValueMarker yMarker = new org.jfree.chart.plot.ValueMarker(y0);
            yMarker.setPaint(java.awt.Color.BLACK);
            yMarker.setStroke(new java.awt.BasicStroke(2.0f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_BEVEL, 0, new float[]{8.0f, 8.0f}, 0));
            yMarker.setLabel(String.format("y=%.0f", y0));
            yMarker.setLabelAnchor(RectangleAnchor.BOTTOM_LEFT);
            yMarker.setLabelTextAnchor(TextAnchor.TOP_LEFT);
            plot.addRangeMarker(yMarker);
        }

        // 拼接两个chart
        java.awt.image.BufferedImage image1 = chart.createBufferedImage(width, height);
        java.awt.image.BufferedImage image2 = chart90.createBufferedImage(width, height);
        int totalWidth = width * 2;
        java.awt.image.BufferedImage combined = new java.awt.image.BufferedImage(totalWidth, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = combined.createGraphics();
        g2.drawImage(image1, 0, 0, null);
        g2.drawImage(image2, width, 0, null);
        g2.dispose();

        javax.imageio.ImageIO.write(combined, "png", outputFile);
    }

    // 用于从一个jsonObject中获取事务执行的系统代码时间（即不包含用户代码的时间）
    private long getSystemTime(JSONObject object) {
        long systemTime = 0;
        JSONArray array = object.getJSONArray("sub phases");
        for (int i = 0; i < array.size(); i++) {
            JSONObject subObject = array.getJSONObject(i);
            if (!subObject.getString("phase description").equals("gap")) {
                systemTime += subObject.getLong("duration");
            }
        }
        return systemTime;
    }

    // 用于分析总体情况，输出2条折线，一条是总时间，另一条是不包含用户代码的时间
    public void systemTimeAnalysis(ArrayList<ArrayList<Long>> calculatedTime, JSONObject newObject, File outputDir, String chartTitle, boolean finished) throws IOException {
        if (newObject != null) {
            ArrayList<Long> time = new ArrayList<>();
            time.add(getTotalTime(newObject));
            time.add(getSystemTime(newObject));
            calculatedTime.add(time);
        }
        if (finished) {
            ArrayList<String> lineNames = new ArrayList<>();
            lineNames.add("total time");
            lineNames.add("system time");
            draw(outputDir, calculatedTime, chartTitle, lineNames);
        }
    }

    // 用于从一个jsonObject中获取entity锁获取的时间（包括获取锁过程中的等待时间）
    private long getBigLockAcquisitionTime(JSONObject object) {
        long bigLockAcquisitionTime = 0;
        JSONArray array = object.getJSONArray("sub phases");
        for (int i = 0; i < array.size(); i++) {
            JSONObject subObject = array.getJSONObject(i);
            String description = subObject.getString("phase description");
            // 通过用户接口调用粗粒度锁包含整段
            if (description.equals("acquire big lock")) {
                bigLockAcquisitionTime += subObject.getLong("duration");
            }
            // 以及通过用户接口调用获取细粒度锁和设置时态属性的获取共享锁阶段
            else if (description.equals("acquire temporal lock") || description.equals("set temporal property")) {
                JSONArray subArray = subObject.getJSONArray("sub phase");
                if (subArray == null) continue;
                for (int j = 0; j < subArray.size(); j++) {
                    JSONObject subObject2 = subArray.getJSONObject(j);
                    String subDescription = subObject2.getString("phase description");
                    if (subDescription.equals("acquire shared lock")) {
                        bigLockAcquisitionTime += subObject2.getLong("duration");
                    }
                }
            }
        }
        return bigLockAcquisitionTime;
    }

    // 用于分析粗粒度锁的情况，两条线，一条总时间，一条粗粒度锁获取时间
    public void bigLockAcquisitionTimeAnalysis(ArrayList<ArrayList<Long>> calculatedTime, JSONObject newObject, File outputDir, String chartTitle, boolean finished) throws IOException {
        if (newObject != null) {
            ArrayList<Long> time = new ArrayList<>();
            time.add(getTotalTime(newObject));
            time.add(getBigLockAcquisitionTime(newObject));
            calculatedTime.add(time);
        }
        if (finished) {
            ArrayList<String> lineNames = new ArrayList<>();
            lineNames.add("total time");
            lineNames.add("big lock acquisition time");
            draw(outputDir, calculatedTime, chartTitle, lineNames);
        }
    }

    // 用于从一个jsonObject中获取时态锁获取的时间（包括获取锁过程中的等待时间）
    private long getTemporalLockAcquisitionTime(JSONObject object) {
        long temporalLockAcquisitionTime = 0;
        JSONArray array = object.getJSONArray("sub phases");
        for (int i = 0; i < array.size(); i++) {
            JSONObject subObject = array.getJSONObject(i);
            String description = subObject.getString("phase description");
            // 通过用户接口调用获取细粒度锁的获取时态锁阶段
            if (description.equals("acquire temporal lock")) {
                JSONArray subArray = subObject.getJSONArray("sub phase");
                if (subArray == null) continue;
                for (int j = 0; j < subArray.size(); j++) {
                    JSONObject subObject2 = subArray.getJSONObject(j);
                    String subDescription = subObject2.getString("phase description");
                    if (subDescription.equals("acquire temporal lock")) {
                        temporalLockAcquisitionTime += subObject2.getLong("duration");
                    }
                }
            }
            // 以及通过用户接口调用设置时态属性的获取时态锁阶段
            else if (description.equals("set temporal property")) {
                JSONArray subArray = subObject.getJSONArray("sub phase");
                if (subArray == null) continue;
                for (int j = 0; j < subArray.size(); j++) {
                    JSONObject subObject2 = subArray.getJSONObject(j);
                    String subDescription = subObject2.getString("phase description");
                    if (subDescription.equals("acquire temporal exclusive lock")) {
                        temporalLockAcquisitionTime += subObject2.getLong("duration");
                    }
                }
            }
        }
        return temporalLockAcquisitionTime;
    }

    // 用于分析细粒度锁的情况，两条线，一条总时间，一条细粒度锁获取时间
    public void temporalLockAcquisitionTimeAnalysis(ArrayList<ArrayList<Long>> calculatedTime, JSONObject newObject, File outputDir, String chartTitle, boolean finished) throws IOException {
        if (newObject != null) {
            ArrayList<Long> time = new ArrayList<>();
            time.add(getTotalTime(newObject));
            time.add(getTemporalLockAcquisitionTime(newObject));
            calculatedTime.add(time);
        }
        if (finished) {
            ArrayList<String> lineNames = new ArrayList<>();
            lineNames.add("total time");
            lineNames.add("temporal lock acquisition time");
            draw(outputDir, calculatedTime, chartTitle, lineNames);
        }
    }

    // 用于从一个jsonObject中获取所有不包含锁操作和时态属性相关操作的系统操作的时间
    private long getNoLockNoTemporalOperationSystemTime(JSONObject object) {
        long noLockNoTemporalOperationSystemTime = 0;
        JSONArray array = object.getJSONArray("sub phases");
        for (int i = 0; i < array.size(); i++) {
            JSONObject subObject = array.getJSONObject(i);
            String description = subObject.getString("phase description");
            // 用户代码的时间跳过
            if (description.contains("gap")) continue;
            // 锁操作相关时间跳过
            if (description.contains("lock")) continue;
            // 提交阶段包含写存储和锁释放两个大段，锁释放不考虑，写存储都是关于时态数据的部分的，所以这段直接跳过
            if (description.contains("commit")) continue;
            // 结束阶段如果有释放锁则去掉释放锁的时间
            if (description.contains("close")) {
                long duration = subObject.getLong("duration");
                JSONArray subArray = subObject.getJSONArray("sub phases");
                if (subArray == null) continue;
                for (int j = 0; j < subArray.size(); j++) {
                    JSONObject subObject2 = subArray.getJSONObject(j);
                    String subDescription = subObject2.getString("phase description");
                    if (subDescription.equals("release lock")) {
                        duration -= subObject2.getLong("duration");
                    }
                }
                noLockNoTemporalOperationSystemTime += duration;
                continue;
            }
            if (description.contains("set temporal property")) {
                JSONArray subArray = subObject.getJSONArray("sub phases");
                if (subArray == null) continue;
                for (int j = 0; j < subArray.size(); j++) {
                    JSONObject subObject2 = subArray.getJSONObject(j);
                    String subDescription = subObject2.getString("phase description");
                    if (subDescription.equals("acquire token") || subDescription.equals("examine type")) {
                        noLockNoTemporalOperationSystemTime += subObject2.getLong("duration");
                    }
                }
                continue;
            }
            if (description.contains("get temporal property")) {
                JSONArray subArray = subObject.getJSONArray("sub phases");
                if (subArray == null) continue;
                for (int j = 0; j < subArray.size(); j++) {
                    JSONObject subObject2 = subArray.getJSONObject(j);
                    String subDescription = subObject2.getString("phase description");
                    if (subDescription.equals("acquire token")) {
                        noLockNoTemporalOperationSystemTime += subObject2.getLong("duration");
                    }
                }
                continue;
            }
            // 其他阶段都是无锁操作和时态操作的阶段
            noLockNoTemporalOperationSystemTime += subObject.getLong("duration");
        }
        return noLockNoTemporalOperationSystemTime;
    }

    // 用于分析系统读写的情况，两条线，一条总时间，一条不包含锁操作和时态数据操作的系统时间
    public void noLockNoTemporalOperationTimeAnalysis(ArrayList<ArrayList<Long>> calculatedTime, JSONObject newObject, File outputDir, String chartTitle, boolean finished) throws IOException {
        if (newObject != null) {
            ArrayList<Long> time = new ArrayList<>();
            time.add(getTotalTime(newObject));
            time.add(getNoLockNoTemporalOperationSystemTime(newObject));
            calculatedTime.add(time);
        }
        if (finished) {
            ArrayList<String> lineNames = new ArrayList<>();
            lineNames.add("total time");
            lineNames.add("no lock no temporal operation time");
            draw(outputDir, calculatedTime, chartTitle, lineNames);
        }
    }

    // 用于从一个jsonObject中获取所有时态读写的时间
    private long getTemporalReadWriteTime(JSONObject object) {
        long noLockNoTemporalOperationSystemTime = 0;
        JSONArray array = object.getJSONArray("sub phases");
        for (int i = 0; i < array.size(); i++) {
            JSONObject subObject = array.getJSONObject(i);
            String description = subObject.getString("phase description");
            // 提交阶段包含写存储和锁释放两个大段，锁释放不考虑
            if (description.contains("commit")) {
                long duration = subObject.getLong("duration");
                JSONArray subArray = subObject.getJSONArray("sub phases");
                if (subArray == null) continue;
                for (int j = 0; j < subArray.size(); j++) {
                    JSONObject subObject2 = subArray.getJSONObject(j);
                    String subDescription = subObject2.getString("phase description");
                    if (subDescription.equals("release lock")) {
                        duration -= subObject2.getLong("duration");
                    }
                }
                noLockNoTemporalOperationSystemTime += duration;
            }
            // 设置时态属性阶段仅考虑写缓冲区的时间
            else if (description.contains("set temporal property")) {
                JSONArray subArray = subObject.getJSONArray("sub phases");
                if (subArray == null) continue;
                for (int j = 0; j < subArray.size(); j++) {
                    JSONObject subObject2 = subArray.getJSONObject(j);
                    String subDescription = subObject2.getString("phase description");
                    if (subDescription.equals("write buffer")) {
                        noLockNoTemporalOperationSystemTime += subObject2.getLong("duration");
                    }
                }
            }
            // 获取时态属性阶段仅考虑真正获取时态属性的阶段
            else if (description.contains("get temporal property")) {
                JSONArray subArray = subObject.getJSONArray("sub phases");
                if (subArray == null) continue;
                for (int j = 0; j < subArray.size(); j++) {
                    JSONObject subObject2 = subArray.getJSONObject(j);
                    String subDescription = subObject2.getString("phase description");
                    if (subDescription.equals("acquire data")) {
                        noLockNoTemporalOperationSystemTime += subObject2.getLong("duration");
                    }
                }
            }

        }
        return noLockNoTemporalOperationSystemTime;
    }

    // 用于分析时态数据读写的情况，两条线，一条总时间，一条时态数据读写时间
    public void temporalReadWriteTimeAnalysis(ArrayList<ArrayList<Long>> calculatedTime, JSONObject newObject, File outputDir, String chartTitle, boolean finished) throws IOException {
        if (newObject != null) {
            ArrayList<Long> time = new ArrayList<>();
            time.add(getTotalTime(newObject));
            time.add(getTemporalReadWriteTime(newObject));
            calculatedTime.add(time);
        }
        if (finished) {
            ArrayList<String> lineNames = new ArrayList<>();
            lineNames.add("total time");
            lineNames.add("temporal read write time");
            draw(outputDir, calculatedTime, chartTitle, lineNames);
        }
    }

    public void drawTpm(HashMap<Integer, Double> tpmMap, File output) {
        try {
            // 构造数据集
            org.jfree.data.category.DefaultCategoryDataset dataset = new org.jfree.data.category.DefaultCategoryDataset();
            ArrayList<Integer> threadList = new ArrayList<>(tpmMap.keySet());
            Collections.sort(threadList);
            for (Integer thread : threadList) {
                dataset.addValue(tpmMap.get(thread), "TPM", thread);
            }

            org.jfree.chart.JFreeChart barChart = org.jfree.chart.ChartFactory.createBarChart(
                    "TPM of different Thread Num",
                    "Threads",
                    "TPM",
                    dataset,
                    org.jfree.chart.plot.PlotOrientation.VERTICAL,
                    false, true, false
            );

            // 设置每个柱子上显示数值
            org.jfree.chart.plot.CategoryPlot plot = barChart.getCategoryPlot();
            org.jfree.chart.renderer.category.BarRenderer renderer = (org.jfree.chart.renderer.category.BarRenderer) plot.getRenderer();
            renderer.setDefaultItemLabelsVisible(true);
            renderer.setDefaultItemLabelGenerator(new org.jfree.chart.labels.StandardCategoryItemLabelGenerator());
            renderer.setDefaultItemLabelFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 16));

            int width = 100 + 80 * threadList.size();
            int height = 600;
            org.jfree.chart.ChartUtils.saveChartAsPNG(output, barChart, width, height);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void totalAnalysis(File txDataDir, File resultPictureDir) throws IOException {
        // resultPictureDir指向一个空文件夹
        if (!resultPictureDir.exists()) {
            if (!resultPictureDir.mkdir()) throw new RuntimeException();
        }
        else if (!resultPictureDir.isDirectory()) throw new RuntimeException();
        else if (Objects.requireNonNull(resultPictureDir.listFiles()).length != 0) throw new RuntimeException();
        // 按照事务类型-分析类型-线程数组织结果图片的文件名
        HashMap<Integer, HashMap<String, File>> txDataFiles = new HashMap<>();
        HashMap<Integer, Double> tpmMap = new HashMap<>();
        for (File threadDir : Objects.requireNonNull(txDataDir.listFiles())) {
            int thread = Integer.parseInt(threadDir.getName().replace("thread_", ""));
            HashMap<String, File> temp = txDataFiles.computeIfAbsent(thread, k -> new HashMap<>());
            for (File txDataFile : Objects.requireNonNull(threadDir.listFiles())) {
                String fileName = txDataFile.getName();
                if (fileName.contains(".jsonl")) {
                    temp.put(fileName.replace(".jsonl", ""), txDataFile);
                }
                else if (fileName.equals("tpm.txt")) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(txDataFile))) {
                        double tpm = Double.parseDouble(reader.readLine());
                        tpmMap.put(thread, tpm);
                    }
                }
            }
        }
        for (Map.Entry<Integer, HashMap<String, File>> entry : txDataFiles.entrySet()) {
            int thread = entry.getKey();
            String threadString = String.format("%2d_thread", thread);
            for (Map.Entry<String, File> subEntry : entry.getValue().entrySet()) {
                String txType = subEntry.getKey();
                System.out.printf("start processing result of %s, %s\n", threadString, txType);
                File txDataFile = subEntry.getValue();
                ArrayList<ArrayList<Long>> systemTimeList = new ArrayList<>();
                String systemTimeChartTitle = txType + "-" + "1_system_time" + "-" + threadString;
                File systemTimePngFile = new File(resultPictureDir, systemTimeChartTitle + ".png");
                ArrayList<ArrayList<Long>> bigLockAcquisitionTimeList = new ArrayList<>();
                String bigLockAcquisitionTimeChartTitle = txType + "-" + "2_big_lock_acquisition_time" + "-" + threadString;
                File bigLockAcquisitionTimePngFile = new File(resultPictureDir, bigLockAcquisitionTimeChartTitle + ".png");
                ArrayList<ArrayList<Long>> temporalLockAcquisitionTimeList = new ArrayList<>();
                String temporalLockAcquisitionTimeChartTitle = txType + "-" + "3_temporal_lock_acquisition_time" + "-" + threadString;
                File temporalLockAcquisitionTimePngFile = new File(resultPictureDir, temporalLockAcquisitionTimeChartTitle + ".png");
                ArrayList<ArrayList<Long>> noLockNoTemporalOperationTimeList = new ArrayList<>();
                String noLockNoTemporalOperationTimeChartTitle = txType + "-" + "4_no_lock_no_temporal_operation_time" + "-" + threadString;
                File noLockNoTemporalOperationTimePngFile = new File(resultPictureDir, noLockNoTemporalOperationTimeChartTitle + ".png");
                ArrayList<ArrayList<Long>> temporalReadWriteTimeList = new ArrayList<>();
                String temporalReadWriteTimeChartTitle = txType + "-" + "5_temporal_read_write_time" + "-" + threadString;
                File temporalReadWriteTimePngFile = new File(resultPictureDir, temporalReadWriteTimeChartTitle + ".png");
                int processedObjectNum = 0;
                try (BufferedReader reader = new BufferedReader(new FileReader(txDataFile))) {
                    String line;
                    while (true) {
                        line = reader.readLine();
                        boolean finished = ((line== null) || line.equals(""));
                        JSONObject object = JSONObject.parseObject(line);
                        systemTimeAnalysis(systemTimeList, object, systemTimePngFile, systemTimeChartTitle, finished);
                        bigLockAcquisitionTimeAnalysis(bigLockAcquisitionTimeList, object, bigLockAcquisitionTimePngFile, bigLockAcquisitionTimeChartTitle, finished);
                        temporalLockAcquisitionTimeAnalysis(temporalLockAcquisitionTimeList, object, temporalLockAcquisitionTimePngFile, temporalLockAcquisitionTimeChartTitle, finished);
                        noLockNoTemporalOperationTimeAnalysis(noLockNoTemporalOperationTimeList, object, noLockNoTemporalOperationTimePngFile, noLockNoTemporalOperationTimeChartTitle, finished);
                        temporalReadWriteTimeAnalysis(temporalReadWriteTimeList, object, temporalReadWriteTimePngFile, temporalReadWriteTimeChartTitle, finished);
                        processedObjectNum++;
                        if (processedObjectNum % 100 == 0) System.out.println(processedObjectNum + " transaction results processed");
                        if (finished) break;
                    }
                }
            }
        }
        drawTpm(tpmMap, new File(resultPictureDir, "tpm.png"));
    }

    public static void main(String[] args) throws IOException {
        TxStatisticAnalyzer analyzer = new TxStatisticAnalyzer();
        analyzer.totalAnalysis(new File(Helper.mustEnv("TX_DATA_DIR")), new File(Helper.mustEnv("RESULT_DIR")));
    }
}
