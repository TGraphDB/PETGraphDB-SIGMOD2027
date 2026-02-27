package edu.buaa.utils;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogAnalyzer {
    // 这个类用于TIM-Tree的和LSM-Tree对比的内部实验，分析TPS和RocksDB的build的日志输出
    // 匹配这类串：[2026-02-23T07:17:33.582Z] 15:17:33.873 DEBUG，是debug log的标准前缀
    private static final String LOG_PREFIX_PATTERN = "\\[([\\d\\-T:.Z]+)\\]\\s+[\\d:.]+\\s+DEBUG\\s+";
    // RocksDB的两种debug输出格式
    private static final String ROCKSDB_LOG = "RocksDBBulkLoad\\s+-\\s+loading\\s+(?:node|rel)\\s+temporal:\\s+[\\d.]+%?,\\s+total\\s+points:\\s+(\\d+)";
    // TPS的debug输出格式
    private static final String TPS_LOG = "TPSBulkLoad\\s+-\\s+loading\\s+tp:\\s+[\\d.]+%?,\\s+total\\s+points:\\s+(\\d+)";
    public static final Pattern ROCKSDB_PATTERN = Pattern.compile(LOG_PREFIX_PATTERN + ROCKSDB_LOG);
    public static final Pattern TPS_PATTERN = Pattern.compile(LOG_PREFIX_PATTERN + TPS_LOG);

    private static Pair<Instant, Long> parseLine(String line, Pattern pattern) {
        if (line == null) return null;

        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) return null;

        try {
            Instant timestamp = Instant.parse(matcher.group(1));
            long points = Long.parseLong(matcher.group(2));
            return Pair.of(timestamp, points);
        } catch (Exception e) {
            return null;
        }
    }

    // 间隔秒数->期间导入了多少数据点，按照时间的先后排列
    private final ArrayList<Pair<Double, Long>> data = new ArrayList<>();

    // 不对边界情况负责
    public LogAnalyzer(File logFile, Pattern analyzePattern) throws IOException {
        Pair<Instant, Long> lastPair = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Pair<Instant, Long> pair = parseLine(line, analyzePattern);
                if (pair == null) continue;
                if (lastPair == null) {
                    lastPair = pair;
                    continue;
                }
                // 以秒为单位的时间差计算
                double timeDiff = ((double) Duration.between(lastPair.getKey(), pair.getKey()).toMillis()) / 1000.0;
                long dataPoints;
                // rocksdb会分两段计数，所以在边界处需要更新，边界判定为导入数据量突然变小了
                if (lastPair.getValue() > pair.getValue()) {
                    dataPoints = pair.getValue();
                }
                else {
                    dataPoints = pair.getValue() - lastPair.getValue();
                }
                data.add(Pair.of(timeDiff, dataPoints));
                lastPair = pair;
            }
        }
    }

    // 同样不对边界情况负责，且不对step过小的情况负责：step的两端不可以包含在同一段中，即step要大于最小的data.get(i).getValue()
    private ArrayList<Double> getDurationsAtFixedPointStep(long step) {
        for (Pair<Double, Long> pair : data) {
            if (pair.getValue() > step) {
                System.out.println("step is too small!");
                return new ArrayList<>();
            }
        }
        ArrayList<Double> result = new ArrayList<>();
        double totalTime = 0;
        long leftPoints = step;
        for (Pair<Double, Long> pair : data) {
            double time = pair.getKey();
            long points = pair.getValue();
            if (leftPoints > points) {
                leftPoints -= points;
                totalTime += time;
            }
            else if (leftPoints == points) {
                totalTime += time;
                leftPoints = step;
                result.add(totalTime);
                totalTime = 0;
            }
            else {
                // 直接假设这段pair的时间内time和points是线性的，分两段，一段加到这个结果中，另一段加到下一个结果中
                long thisPoints = leftPoints;
                long nextPoints = points - leftPoints;
                double thisTime = time / points * thisPoints;
                double nextTime = time / points * nextPoints;
                result.add(totalTime + thisTime);
                totalTime = nextTime;
                leftPoints = step - nextPoints;
            }
        }
        // 大概率边界还有一些没处理完成的点，直接忽略掉，因为不对边界负责
        return result;
    }

    public static void draw(long step, File output, HashMap<String, LogAnalyzer> logs) throws IOException {
        XYSeriesCollection dataset = new XYSeriesCollection();
        for (Map.Entry<String, LogAnalyzer> entry : logs.entrySet()){
            ArrayList<Double> timeList = entry.getValue().getDurationsAtFixedPointStep(step);
            XYSeries series = new XYSeries(entry.getKey());

            for (int i = 0; i < timeList.size(); i++) {
                series.add(i + 1, timeList.get(i));
            }

            dataset.addSeries(series);
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                "title",
                "data points (*" + String.format("%,d", step) + ")",
                "time (s)",
                dataset
        );

        // 保存到文件
        if (output != null) {
            ChartUtils.saveChartAsPNG(output, chart, 800, 600);
        }

        // 展示窗口
        JFrame frame = new JFrame("Chart");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.add(new ChartPanel(chart));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws IOException {
        File logFile = new File("D:\\example\\log");
        LogAnalyzer logAnalyzer = new LogAnalyzer(logFile, ROCKSDB_PATTERN);
        HashMap<String, LogAnalyzer> logs = new HashMap<>();
        logs.put("ROCKSDBM64", logAnalyzer);
        draw(100_000_000, null, logs);
    }
}
