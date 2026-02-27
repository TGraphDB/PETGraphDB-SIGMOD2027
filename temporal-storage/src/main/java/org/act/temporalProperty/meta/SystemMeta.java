package org.act.temporalProperty.meta;

import org.act.temporalProperty.exception.TPSRuntimeException;
import org.act.temporalProperty.helper.StoreLock;
import org.act.temporalProperty.impl.*;
import org.act.temporalProperty.index.IndexMetaManager;
import org.act.temporalProperty.index.IndexStore;
import org.act.temporalProperty.index.value.IndexMetaData;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by song on 2018-01-17.
 */
public class SystemMeta {
    public final StoreLock lock = new StoreLock();

    private final Map<Integer, PropertyMetaData> properties = new HashMap<>();
    private final Set<IndexMetaData> indexes = new HashSet<>();
    private long indexNextId;
    private long indexNextFileId;

    private final Map<Integer, SinglePropertyStore> propertyStores = new HashMap<>();
    private IndexMetaManager indexMetas;
    private File dbDir;

    public SystemMeta(){

    }

    public SinglePropertyStore getStore(int propertyId){
        SinglePropertyStore store = propertyStores.get(propertyId);
        if(store==null){
            throw new TPSRuntimeException("no such property id: {}. should create first!", propertyId);
        }
        return store;
    }

    public Map<Integer, SinglePropertyStore> proStores(){return propertyStores;}

    public void addStore(int propertyId, SinglePropertyStore store){
        propertyStores.put(propertyId, store);
    }

    public Map<Integer, PropertyMetaData> getProperties() {
        return properties;
    }

    public Set<IndexMetaData> getIndexes() {
        return indexes;
    }

    public void addIndex(IndexMetaData iMeta) {
        indexes.add(iMeta);
    }

    public void addProperty(PropertyMetaData pMeta) {
        properties.put(pMeta.getPropertyId(), pMeta);
    }

    public void force(File dir) throws IOException {
        this.setIndexNextFileId(indexMetas.getNextFileId());
        this.setIndexNextId(indexMetas.getNextIndexId());
        this.indexes.clear();
        this.indexes.addAll(indexMetas.allIndexes());
        SystemMetaController.forceToDisk(dir, this);
    }

    public void initStore(File storeDir, TableCache cache, IndexMetaManager indexMetaManager, IndexStore indexStore) throws Exception {
        this.dbDir = storeDir;
        this.indexMetas = indexMetaManager;
        for( PropertyMetaData pMeta : properties.values()){
            SinglePropertyStore onePropStore = new SinglePropertyStore(pMeta, storeDir, cache, indexStore);
            propertyStores.put(pMeta.getPropertyId(), onePropStore);
        }
    }

    @Override
    public String toString() {
        return "SystemMeta{" +
                "properties=" + properties +
                ", indexes=" + indexes +
                ", dbDir=" + dbDir +
                '}';
    }

    public long indexNextId() {
        return this.indexNextId;
    }

    public void setIndexNextId(long indexNextId) {
        this.indexNextId = indexNextId;
    }

    public long indexNextFileId()
    {
        return this.indexNextFileId;
    }

    public void setIndexNextFileId( long nextFileId )
    {
        this.indexNextFileId = nextFileId;
    }

    private boolean stableMemTableOnline =false;
    public boolean hasStableMemTable() {
        return stableMemTableOnline;
    }

    public void setStableMemTable(boolean online){
        stableMemTableOnline = online;
    }
}
