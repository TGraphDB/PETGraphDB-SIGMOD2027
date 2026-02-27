package edu.buaa.server.store;

import com.alibaba.fastjson.JSON;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 这个类主要是在底层存储对比系统中将实体和属性的用户id/名字转换为内部id用的，同时也管理其他的静态属性。
 * 为了公平对比，所有的存储相关的对比系统都使用这个类管理静态属性。
 * 但如果图的静态内容过大的话，这里面的东西可能就得修改了。
 */
public class StaticDataManager {
    private final Map<String, Long> entityIdMap = new HashMap<>();
    private final Map<String, Integer> propertyNameIdMap = new HashMap<>();
    private final Map<Long, Map<String, Object>> staticProperties = new HashMap<>();
    private final File entityFile, propertyFile, staticPropertyFile;

    public StaticDataManager(File dbDir) {
        createDirIfNotExists(dbDir);
        entityFile = new File(dbDir, "entity.json");
        propertyFile = new File(dbDir, "property.json");
        staticPropertyFile = new File(dbDir, "static_property.json");
        loadData();
    }

    private static void createDirIfNotExists(File dir) {
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IllegalArgumentException("invalid dbDir");
            }
        } else if (!dir.isDirectory()) {
            throw new IllegalArgumentException("invalid dbDir");
        }
    }

    public void shutdown() {
        flush();
    }

    @SuppressWarnings("unchecked")
    private void loadData() {
        if (entityFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(entityFile))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append(System.lineSeparator());
                }
                Map<String, Object> map = JSON.parseObject(builder.toString());
                entityIdMap.clear();
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    entityIdMap.put(entry.getKey(), Long.parseLong(entry.getValue().toString()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (propertyFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(propertyFile))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append(System.lineSeparator());
                }
                Map<String, Object> map = JSON.parseObject(builder.toString());
                propertyNameIdMap.clear();
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    propertyNameIdMap.put(entry.getKey(), (Integer) entry.getValue());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (staticPropertyFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(staticPropertyFile))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append(System.lineSeparator());
                }
                Map<String, Object> map = JSON.parseObject(builder.toString());
                staticProperties.clear();
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    staticProperties.put(Long.parseLong(entry.getKey()), (Map<String, Object>) entry.getValue());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void flush() {
        try (FileWriter writer = new FileWriter(entityFile)) {
            writer.write(JSON.toJSONString(entityIdMap));
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (FileWriter writer = new FileWriter(propertyFile)) {
            writer.write(JSON.toJSONString(propertyNameIdMap));
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (FileWriter writer = new FileWriter(staticPropertyFile)) {
            writer.write(JSON.toJSONString(staticProperties));
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createEntityId(String userId) {
        if (entityIdMap.containsKey(userId)) throw new RuntimeException("entity already exists");
        entityIdMap.put(userId, (long) entityIdMap.size());
    }

    public long getEntityId(String userId) {
        return entityIdMap.get(userId);
    }

    public Map<String, Long> getEntityIdMap() {
        return entityIdMap;
    }

    // 仅负责管理时态属性的属性名和id的对应关系
    public int getOrCreatePropertyId(String propertyName) {
        if (!propertyNameIdMap.containsKey(propertyName)) {
            propertyNameIdMap.put(propertyName, propertyNameIdMap.size());
        }
        return propertyNameIdMap.get(propertyName);
    }

    public void setStaticProperties(String entityId, Map<String, Object> staticProperties) {
        this.staticProperties.put(entityIdMap.get(entityId), staticProperties);
    }
}
