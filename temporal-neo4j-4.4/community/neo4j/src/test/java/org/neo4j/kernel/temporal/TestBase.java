package org.neo4j.kernel.temporal;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

public abstract class TestBase {
    private static final String DB = "db";
    private static final String DATA = "data";

    private static final File testHomeDir;
    private static final HashSet<Class<? extends Annotation>> concernedMethodAnnotations;

    // 第一级map的key为子类，第二级map的key为子类的关心的方法（打上测试注释），其value为方法的根目录和加载后的所有数据生成器
    private static final HashMap<Class<? extends TestBase>, HashMap<Method, Pair<File, Iterator<DataGeneratorRecorder>>>> methodDirs = new HashMap<>();

    static {
        String testHome = System.getenv("TGraphTestHome");
        testHomeDir = new File(testHome);
        validateDir(testHomeDir);
        concernedMethodAnnotations = new HashSet<>();
        concernedMethodAnnotations.add(Test.class);
        concernedMethodAnnotations.add(ParameterizedTest.class);
        concernedMethodAnnotations.add(RepeatedTest.class);
    }

    private static void validateDir(File dir) {
        if (dir.exists()) {
            assert dir.isDirectory();
        }
        else {
            assert dir.mkdir();
        }
    }

    protected static void prepareDir(Class<? extends TestBase> clazz) {
        File testClassDir = testHomeDir.toPath().resolve(clazz.getName()).toFile();
        validateDir(testClassDir);
        HashMap<Method, Pair<File, Iterator<DataGeneratorRecorder>>> methodFiles;
        if (methodDirs.containsKey(clazz)) {
            methodFiles = methodDirs.get(clazz);
        }
        else {
            methodFiles = new HashMap<>();
            methodDirs.put(clazz, methodFiles);
        }
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            boolean concern = false;
            for (Class<? extends Annotation> annotation : concernedMethodAnnotations) {
                if (method.isAnnotationPresent(annotation)) {
                    concern = true;
                    break;
                }
            }
            if (!concern) continue;
            File methodDir = testClassDir.toPath().resolve(method.getName()).toFile();
            validateDir(methodDir);
            File dataDir = methodDir.toPath().resolve(DATA).toFile();
            validateDir(dataDir);
            methodFiles.put(method, Pair.of(methodDir, getDataGenerator(dataDir)));
        }
    }

    private static Iterator<DataGeneratorRecorder> getDataGenerator(File dataDir) {
        ArrayList<DataGeneratorRecorder> temp = new ArrayList<>();
        File[] dataFiles = dataDir.listFiles();
        assert dataFiles != null;
        for (File dataFile : dataFiles) {
            temp.add(new DataGeneratorRecorder(dataDir, dataFile));
        }
        return temp.iterator();
    }

    protected static File getDatabaseHome(Class<? extends TestBase> clazz, Method method) {
        try {
            return methodDirs.get(clazz).get(method).getLeft().toPath().resolve(DB).toFile();
        }
        catch (NullPointerException e) {
            return null;
        }
    }

    protected static void clearDatabase(Class<? extends TestBase> clazz, Method method) {
        File dbDir;
        try {
            dbDir = methodDirs.get(clazz).get(method).getLeft().toPath().resolve(DB).toFile();
        }
        catch (NullPointerException e) {
            return;
        }
        removeFile(dbDir);
        assert dbDir.mkdir();
        dbDir = new File(dbDir.getParent(), "db_crash");
        if (dbDir.exists()) {
            removeFile(dbDir);
            assert dbDir.mkdir();
        }
    }

    private static void removeFile(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] subFiles = file.listFiles();
            assert subFiles != null;
            for (File subFile : subFiles) {
                removeFile(subFile);
            }
        }
        assert file.delete() : "清除数据库失败，文件" + file + "无法删除";
    }

    private static File getDataHome(Class<? extends TestBase> clazz, Method method) {
        try {
            return methodDirs.get(clazz).get(method).getLeft().toPath().resolve(DATA).toFile();
        }
        catch (NullPointerException e) {
            return null;
        }
    }

    protected static DataGeneratorRecorder getNextDataGenerator(Class<? extends TestBase> clazz, Method method) {
        try {
            Iterator<DataGeneratorRecorder> temp = methodDirs.get(clazz).get(method).getRight();
            if (temp.hasNext()) {
                return temp.next();
            }
        }
        catch (NullPointerException ignored) {}
        return new DataGeneratorRecorder(Objects.requireNonNull(getDataHome(clazz, method)));
    }
}
