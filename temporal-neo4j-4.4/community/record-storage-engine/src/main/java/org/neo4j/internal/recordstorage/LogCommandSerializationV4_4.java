/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.internal.recordstorage;

import org.act.temporalProperty.meta.ValueContentType;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.vo.TimeIntervalValueEntry;
import org.neo4j.io.fs.ReadableChannel;
import org.neo4j.io.fs.WritableChannel;
import org.neo4j.kernel.KernelVersion;

import java.io.IOException;

class LogCommandSerializationV4_4 extends LogCommandSerializationV4_3_D3
{
    static final LogCommandSerializationV4_4 INSTANCE = new LogCommandSerializationV4_4();

    @Override
    KernelVersion version()
    {
        return KernelVersion.V4_4;
    }

    private Slice getSliceEntry(ReadableChannel channel) throws IOException {
        int len = channel.getInt();
        byte[] raw = new byte[ len ];
        channel.get( raw, len );
        return new Slice( raw );
    }

    private void putSliceEntry(WritableChannel channel, Slice in) throws IOException {
        channel.putInt( in.length() );
        channel.put( in.getBytes(), in.length() );
    }

    @Override
    protected Command readNodeTemporalPropertyCommand(ReadableChannel channel) throws IOException {
        Command.NodeTemporalPropertyCommand command = new Command.NodeTemporalPropertyCommand();
        command.init( TimeIntervalValueEntry.decode( getSliceEntry(channel).input() ) );
        return command;
    }

    @Override
    protected Command readRelationshipTemporalPropertyCommand(ReadableChannel channel) throws IOException {
        Command.RelationshipTemporalPropertyCommand command = new Command.RelationshipTemporalPropertyCommand();
        command.init( TimeIntervalValueEntry.decode( getSliceEntry(channel).input() ) );
        return command;
    }

    @Override
    public void writeNodeTemporalPropertyCommand(WritableChannel channel, Command.NodeTemporalPropertyCommand command) throws IOException {
        channel.put( NeoCommandType.NODE_TEMPORAL_PROPERTY_COMMAND );
        putSliceEntry(channel, command.getIntervalEntry().encode());
    }

    @Override
    public void writeRelationshipTemporalPropertyCommand(WritableChannel channel, Command.RelationshipTemporalPropertyCommand command) throws IOException {
        channel.put( NeoCommandType.REL_TEMPORAL_PROPERTY_COMMAND );
        putSliceEntry(channel, command.getIntervalEntry().encode());
    }

    @Override
    protected Command readNodeTemporalPropertyCreateCommand(ReadableChannel channel) throws IOException {
        Command.NodeTemporalPropertyCreateCommand command = new Command.NodeTemporalPropertyCreateCommand();
        int propertyId = channel.getInt();
        ValueContentType type = ValueContentType.decode(channel.getInt());
        command.init(propertyId, type);
        return command;
    }

    @Override
    protected Command readRelationshipTemporalPropertyCreateCommand(ReadableChannel channel) throws IOException {
        Command.RelationshipTemporalPropertyCreateCommand command = new Command.RelationshipTemporalPropertyCreateCommand();
        int propertyId = channel.getInt();
        ValueContentType type = ValueContentType.decode(channel.getInt());
        command.init(propertyId, type);
        return command;
    }

    @Override
    public void writeNodeTemporalPropertyCreateCommand(WritableChannel channel, Command.NodeTemporalPropertyCreateCommand command) throws IOException {
        channel.put( NeoCommandType.NODE_TEMPORAL_PROPERTY_CREATE_COMMAND );
        channel.putInt(command.getPropertyId());
        channel.putInt(command.getType().getId());
    }

    @Override
    public void writeRelationshipTemporalPropertyCreateCommand(WritableChannel channel, Command.RelationshipTemporalPropertyCreateCommand command) throws IOException {
        channel.put( NeoCommandType.REL_TEMPORAL_PROPERTY_CREATE_COMMAND );
        channel.putInt(command.getPropertyId());
        channel.putInt(command.getType().getId());
    }
}
