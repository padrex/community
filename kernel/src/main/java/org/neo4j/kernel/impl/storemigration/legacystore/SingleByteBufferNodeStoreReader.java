/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
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
package org.neo4j.kernel.impl.storemigration.legacystore;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Iterator;

import org.neo4j.helpers.UTF8;
import org.neo4j.helpers.collection.PrefetchingIterator;
import org.neo4j.kernel.impl.nioneo.store.NodeRecord;
import org.neo4j.kernel.impl.nioneo.store.Record;

public class SingleByteBufferNodeStoreReader implements NodeStoreReader
{
    public static final String FROM_VERSION = "NodeStore v0.9.9";
    public static final int RECORD_LENGTH = 9;

    private final FileChannel fileChannel;
    private final long maxId;
    private MappedByteBuffer buffer;

    public SingleByteBufferNodeStoreReader( String fileName ) throws IOException
    {
        fileChannel = new RandomAccessFile( fileName, "r" ).getChannel();
        int endHeaderSize = UTF8.encode( FROM_VERSION ).length;
        maxId = (fileChannel.size() - endHeaderSize) / RECORD_LENGTH;
        buffer = fileChannel.map( FileChannel.MapMode.READ_ONLY, 0, fileChannel.size() );
    }

    @Override
    public long getMaxId()
    {
        return maxId;
    }

    @Override
    public Iterable<NodeRecord> readNodeStore() throws IOException
    {
        return new Iterable<NodeRecord>()
        {
            @Override
            public Iterator<NodeRecord> iterator()
            {
                return new PrefetchingIterator<NodeRecord>()
                {
                    long id = 0;

                    @Override
                    protected NodeRecord fetchNextOrNull()
                    {
                        if ( id >= maxId )
                        {
                            return null;
                        }

                        long inUseByte = buffer.get();

                        boolean inUse = (inUseByte & 0x1) == Record.IN_USE.intValue();
                        NodeRecord nodeRecord = new NodeRecord( id );
                        nodeRecord.setInUse( inUse );
                        long nextRel = LegacyStore.getUnsignedInt( buffer );
                        long nextProp = LegacyStore.getUnsignedInt( buffer );

                        long relModifier = (inUseByte & 0xEL) << 31;
                        long propModifier = (inUseByte & 0xF0L) << 28;

                        nodeRecord.setNextRel( LegacyStore.longFromIntAndMod( nextRel, relModifier ) );
                        nodeRecord.setNextProp( LegacyStore.longFromIntAndMod( nextProp, propModifier ) );

                        id++;
                        return nodeRecord;
                    }

                    @Override
                    public void remove()
                    {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    @Override
    public void close() throws IOException
    {
        fileChannel.close();
    }
}
