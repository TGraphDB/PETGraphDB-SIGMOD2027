package org.neo4j.kernel.temporal;

import org.act.temporalProperty.TemporalPropertyStore;
import org.act.temporalProperty.TemporalPropertyStoreFactory;
import org.act.temporalProperty.impl.MemTable;
import org.act.temporalProperty.index.IndexType;
import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Slice;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

public class TemporalStorageProxy extends LifecycleAdapter {
    private static final int DOWN = 0;
    private static final int READY = 1;
    private static final int RUNNING = 2;

    private TemporalPropertyStore nodeStore;
    private TemporalPropertyStore relStore;
    private final DatabaseLayout layout;
    private final Logger log = LoggerFactory.getLogger(TemporalStorageProxy.class);
    private int status = DOWN;

    public TemporalStorageProxy(DatabaseLayout layout) {
        this.layout = layout;
    }

    @Override
    public void init() throws Exception {
        if (status == READY || status == RUNNING) {
            return;
        }
        this.nodeStore = TemporalPropertyStoreFactory.newPropertyStore( resolveStoreDir( "temporal.node.properties" ) );
        this.relStore = TemporalPropertyStoreFactory.newPropertyStore( resolveStoreDir( "temporal.relationship.properties" ) );
        status = READY;
        log.debug("database " + layout.getDatabaseName() + " temporal module initialized.");
    }

    @Override
    public void start() throws Exception {
        if (status == DOWN) {
            init();
        }
        if (status == RUNNING) {
            return;
        }
        status = RUNNING;
        log.debug("database " + layout.getDatabaseName() + " temporal module started.");
    }

    @Override
    public void stop() throws Exception {
        if (status == DOWN || status == READY) {
            return;
        }
        status = READY;
        log.debug("database " + layout.getDatabaseName() + " temporal module stopped.");
    }

    @Override
    public void shutdown() throws Exception {
        if (status == RUNNING) {
            stop();
        }
        if (status == DOWN) {
            return;
        }
        this.nodeStore.shutDown();
        this.relStore.shutDown();
        status = DOWN;
        log.debug("database " + layout.getDatabaseName() + " temporal module shutdown.");
    }

    private File resolveStoreDir( String folder ) throws IOException {
        File dir = layout.databaseDirectory().resolve(folder).toFile();
        if ( dir.exists() )
        {
            if ( !dir.isDirectory() )
            {
                throw new IOException( folder + " is not a directory." );
            }
        }
        else
        {
            if ( !dir.mkdirs() )
            {
                throw new IOException( "create temporal.node.properties dir failed." );
            }
        }
        return dir;
    }

    public TemporalPropertyStore getNodeStore() {
        return nodeStore;
    }

    public TemporalPropertyStore getRelStore() {
        return relStore;
    }

    public Slice getPoint(TemporalPropertyStore store, TemporalPropertyReadOperation query )
    {
        return store.getPointValue( query.getEntityId(), query.getProId(), query.getStart() );
    }

    public Object getRange( TemporalPropertyStore store, TemporalPropertyReadOperation query, MemTable oneEntityData )
    {
        return store.getRangeValue( query.getEntityId(), query.getProId(), query.getStart(), query.getEnd(), query.callBack(), oneEntityData );
    }

    public Object getAggrIndex( TemporalPropertyStore store, TemporalPropertyReadOperation query, MemTable oneEntityData )
    {
        return store.getByIndex( query.getIndexId(), query.getEntityId(), query.getProId(), query.getStart(), query.getEnd(), oneEntityData );
    }

    public void setValue(TemporalPropertyStore store, TimeIntervalKey intervalKey, Slice value )
    {
        store.setProperty( intervalKey, value );
    }

    public void createAggrMinMaxIndex(TemporalPropertyStore store, int propertyId, TimePointL start, TimePointL end )
    {
        store.createAggrMinMaxIndex( propertyId, start, end, 100, Calendar.MINUTE, IndexType.AGGR_MIN_MAX );
    }

    public void flushAll()
    {
        if ( this.relStore != null )
        {
            this.relStore.flushMemTable2Disk();
            this.relStore.flushMetaInfo2Disk();
        }
        if ( this.nodeStore != null )
        {
            this.nodeStore.flushMemTable2Disk();
            this.nodeStore.flushMetaInfo2Disk();
        }
    }
}
