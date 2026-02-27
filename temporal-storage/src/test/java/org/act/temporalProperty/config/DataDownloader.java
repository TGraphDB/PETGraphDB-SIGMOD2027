//package org.act.temporalProperty.config;
//
//import org.apache.commons.compress.archivers.ArchiveEntry;
//import org.apache.commons.compress.archivers.ArchiveInputStream;
//import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
//import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
//import org.apache.commons.compress.utils.IOUtils;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.BufferedInputStream;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.net.URL;
//import java.nio.channels.Channels;
//import java.nio.channels.ReadableByteChannel;
//import java.nio.file.Files;
//import java.util.ArrayList;
//import java.util.List;
//
//public class DataDownloader {
//    private static String FILE_PATH_SEPARATOR = "\\";
//    private static Logger log = LoggerFactory.getLogger("test");
//    private String urlHeader = "http://chengjinglearn.qiniudn.com/";
//    private String[] fileList = new String[]{
//            "TGraphDemo/20101104.tar.gz",
//            "TGraphDemo/20101105.tar.gz",
//            "TGraphDemo/20101106.tar.gz",
//            "TGraphDemo/20101107.tar.gz",
//            "TGraphDemo/20101108.tar.gz" };
//    private static long[] fileSize = new long[]{67805801};
//
//    public List<File> download(int count) throws IOException {
//        List<File> result = new ArrayList<>(count);
//        File dataDir = new File(TestConfiguration.get().testDataDir());
//        for(int i = 0, fileCount = 0; fileCount < count; i++) {
//            File out = download(urlHeader + fileList[i], dataDir);
//            List<File> files = decompressTarGZip(out, dataDir);
//            int filesToAdd = count-fileCount;
//            result.addAll(files.subList(0, filesToAdd>files.size() ? files.size() : filesToAdd));
//            fileCount += files.size();
//        }
//        return result;
//    }
//
//    private File download(String url, File dir) throws IOException {
//        File out = new File(dir, url.substring(url.length()-8));
//        if(out.exists() && out.isFile()){
//            return out;
//        }
//        URL website = new URL(url);
//        ReadableByteChannel rbc = Channels.newChannel(website.openStream());
//        FileOutputStream fos = new FileOutputStream(out);
//        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
//        return out;
//    }
//
//    /**
//     * Tar文件解压方法
//     *
//     * @param input
//     *            要解压的压缩文件名称（绝对路径名称）
//     * @param targetDir
//     *            解压后文件放置的路径名（绝对路径名称）
//     * @return 解压出的文件列表
//     */
//    private List<File> decompressTarGZip(File input, File targetDir) throws IOException {
//        if (!targetDir.isDirectory() && !targetDir.mkdirs()) {
//            throw new IOException("failed to create directory " + targetDir);
//        }
//        List<File> result = new ArrayList<>();
//        try (InputStream gzi = new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(input)));
//             ArchiveInputStream i = new TarArchiveInputStream(gzi)) {
//            ArchiveEntry entry = null;
//            while ((entry = i.getNextEntry()) != null) {
//                if (!i.canReadEntryData(entry)) {
//                    log.warn("can not read entry");
//                    continue;
//                }
//                String name = targetDir.getAbsolutePath()+FILE_PATH_SEPARATOR+entry.getName();
//                File f = new File(name);
//                if (entry.isDirectory()) {
//                    if (!f.isDirectory() && !f.mkdirs()) {
//                        throw new IOException("failed to create directory " + f);
//                    }
//                } else {
//                    result.add(f);
//                    File parent = f.getParentFile();
//                    if (!parent.isDirectory() && !parent.mkdirs()) {
//                        throw new IOException("failed to create directory " + parent);
//                    }
//                    try (OutputStream o = Files.newOutputStream(f.toPath())) {
//                        IOUtils.copy(i, o);
//                    }
//                }
//            }
//        }
//        return result;
//    }
//
//
//    public static void main(String[] args) throws IOException {
//
//        File f = new File("4.tar.gz");
//        log.info("{}",System.getProperty("user.name"), f.length());
////        DataDownloader me = new DataDownloader();
////        me.download(100);
////        me.decompressTarGZip(new File("4.tar.gz"), new File("."));
//    }
//}
