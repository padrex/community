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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;

import org.neo4j.helpers.UTF8;
import org.neo4j.helpers.collection.PrefetchingIterator;
import org.neo4j.kernel.impl.nioneo.store.NodeRecord;
import org.neo4j.kernel.impl.nioneo.store.Record;

public class BufferedInputStreamNodeStoreReader implements NodeStoreReader
{
    public static final String FROM_VERSION = "NodeStore v0.9.9";
    public static final int RECORD_LENGTH = 9;

    private final long maxId;
    private BufferedInputStream inputStream;

    public BufferedInputStreamNodeStoreReader( String fileName ) throws IOException
    {
        inputStream = new BufferedInputStream( new FileInputStream( fileName ) );
        int endHeaderSize = UTF8.encode( FROM_VERSION ).length;
        maxId = (new File( fileName ).length() - endHeaderSize) / RECORD_LENGTH;
    }

    @Override
    public long getMaxId()
    {
        return maxId;
    }

    @Override
    public Iterable<NodeRecord> readNodeStore() throws IOException
    {
        final byte[] bytes = new byte[RECORD_LENGTH];

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

                        try
                        {
                            long bytesRead = inputStream.read( bytes );
                            if (bytesRead != RECORD_LENGTH)
                            {
                                throw new IllegalStateException( "not enough bytes: " + bytesRead );
                            }
                        }
                        catch ( IOException e )
                        {
                            throw new RuntimeException( e );
                        }

                        byte inUseByte = bytes[0];
                        boolean inUse = (inUseByte & 0x1) == Record.IN_USE.intValue();
                        long nextRel = LegacyStore.readUnsignedInt( bytes, 1 );
                        long nextProp = LegacyStore.readUnsignedInt( bytes, 5 );

                        long relModifier = (inUseByte & 0xEL) << 31;
                        long propModifier = (inUseByte & 0xF0L) << 28;

                        NodeRecord nodeRecord = new NodeRecord( id );
                        nodeRecord.setInUse( inUse );
                        nodeRecord.setNextRel( LegacyStore.longFromIntAndMod( nextRel, relModifier ) );
                        nodeRecord.setNextProp( LegacyStore.longFromIntAndMod( nextProp, propModifier ) );
                        id++;
                        return nodeRecord;
                    }
                };
            }
        };
    }

    @Override
    public void close() throws IOException
    {
        inputStream.close();
    }
}
