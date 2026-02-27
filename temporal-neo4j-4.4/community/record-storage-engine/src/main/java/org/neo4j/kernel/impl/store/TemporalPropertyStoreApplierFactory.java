package org.neo4j.kernel.impl.store;

import org.act.temporalProperty.TemporalPropertyStore;
import org.neo4j.internal.recordstorage.BatchContext;
import org.neo4j.internal.recordstorage.Command;
import org.neo4j.internal.recordstorage.TransactionApplier;
import org.neo4j.internal.recordstorage.TransactionApplierFactory;
import org.neo4j.storageengine.api.CommandsToApply;

import java.io.IOException;

public class TemporalPropertyStoreApplierFactory implements TransactionApplierFactory {
    private final TemporalPropertyStore nodeStore;
    private final TemporalPropertyStore relStore;

    public TemporalPropertyStoreApplierFactory(TemporalPropertyStore nodeStore, TemporalPropertyStore relStore) {
        this.nodeStore = nodeStore;
        this.relStore = relStore;
    }

    @Override
    public TransactionApplier startTx(CommandsToApply transaction, BatchContext batchContext) throws IOException {
        return new TemporalPropertyStoreApplier(nodeStore, relStore);
    }

    static class TemporalPropertyStoreApplier extends TransactionApplier.Adapter {
        private final TemporalPropertyStore nodeStore;
        private final TemporalPropertyStore relStore;

        public TemporalPropertyStoreApplier(TemporalPropertyStore nodeStore, TemporalPropertyStore relStore) {
            this.nodeStore = nodeStore;
            this.relStore = relStore;
        }

        @Override
        public boolean visitRelationshipTemporalPropertyCommand(Command.RelationshipTemporalPropertyCommand command) {
            this.relStore.setProperty(command.getIntervalEntry().getKey(), command.getIntervalEntry().getValue());
            return false;
        }

        @Override
        public boolean visitNodeTemporalPropertyCommand(Command.NodeTemporalPropertyCommand command) {
            this.nodeStore.setProperty(command.getIntervalEntry().getKey(), command.getIntervalEntry().getValue());
            return false;
        }

        @Override
        public boolean visitRelationshipTemporalPropertyCreateCommand(Command.RelationshipTemporalPropertyCreateCommand command) {
            this.relStore.createProperty(command.getPropertyId(), command.getType());
            return false;
        }

        @Override
        public boolean visitNodeTemporalPropertyCreateCommand(Command.NodeTemporalPropertyCreateCommand command) {
            this.nodeStore.createProperty(command.getPropertyId(), command.getType());
            return false;
        }
    }
}
