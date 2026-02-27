package edu.buaa.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.stream.Collectors;

public class TimeoutWatchDog {
    public static void main(String[] args) {
        String logDirStr = System.getenv("DIR_NEO4J_LOG");
        System.out.println("Neo4jLogDelete: DIR_NEO4J_LOG="+logDirStr);
        if(logDirStr!=null && new File(logDirStr).exists()) startNeo4jLogDelete(new File(logDirStr));
        long begin = System.currentTimeMillis();
        long veryBegin = begin;
        int timeoutP = Integer.parseInt(Helper.mustEnv("TIMEOUT_PROGRESS"));
        int timeout = Integer.parseInt(Helper.mustEnv("TIMEOUT"));
        String logPipe = Helper.mustEnv("LOG_PIPE");
        String markerStr = Helper.mustEnv("MARKERS");
        Set<String> markers = new HashSet<>(Arrays.asList(markerStr.split(",")));
        System.out.println("Watchdog search markers: "+marker2print(markers));
        int lastLineCount = 0;
        try{
            for (int i=0; i<1_0000; i++) {
                long now = System.currentTimeMillis();
                File f = new File(logPipe);
                boolean hasProgress;
                if (f.isFile() && f.exists()) {
                    List<String> lines = Files.readAllLines(f.toPath());
                    for(String line : lines){
                        boolean found = markers.removeIf(line::contains);
                        if(found) System.out.println("Watchdog current markers: "+marker2print(markers));
                    }
                    if(markers.isEmpty()) {
                        System.out.println("Watchdog found all markers! Congratulations! Exit...");
                        return;
                    }
                    hasProgress = (lines.size()>lastLineCount);
                    lastLineCount = lines.size();
                    if(hasProgress) begin = now;
                }else{
                    System.out.println("Watchdog: target file not exist, waiting...");
                    hasProgress = false;
                }
                Thread.sleep(1000);
                if(now-begin>timeoutP*1000L && !hasProgress) throw new Exception("WatchDog Timeout! No progress after "+timeoutP+" seconds");
                if(now-veryBegin>timeout*1000L) throw new Exception("WatchDog Timeout! Not finish within "+timeout+" seconds");
            }
            System.err.println("Not finish within 10000s, watch dog dead.");
            System.exit(4);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(3);
        }
    }

    private static String marker2print(Set<String> markers){
        return markers.stream().map(s-> s.replaceAll("_"," ")).collect(Collectors.joining(","));
    }

    private static void startNeo4jLogDelete(File dir){
        Thread t = new Thread(() -> {
            Thread.currentThread().setName("Neo4jLogDelete");
            System.out.println("Watchdog search Neo4j logs in: " + dir.getAbsolutePath());
            try {
                while (dir.exists()) {
                    File[] files = dir.listFiles();
                    if (files != null && files.length > 2) {
                        List<Pair<Path, FileTime>> fileList = new ArrayList<>();
                        for (File file : files) {
                            BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                            FileTime creationTime = attributes.creationTime();
                            fileList.add(Pair.of(file.toPath(), creationTime));
                        }
                        fileList.sort(Comparator.comparing(Pair::getValue));
                        for (int i = 0; i < fileList.size() - 3; i++) try{
                            if (Files.deleteIfExists(fileList.get(i).getKey()))
                                System.out.println("DELETE " + fileList.get(i).getKey());
                        }catch (FileSystemException err){
                            System.out.println(err.getMessage());
                        }
                    }
                    Thread.sleep(10);
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            } catch (InterruptedException e) {
                System.out.println("interrupted");
            }
            System.out.println("Neo4jLogDelete Exit~");
        });
        t.setDaemon(true);
        t.start();
    }
}
