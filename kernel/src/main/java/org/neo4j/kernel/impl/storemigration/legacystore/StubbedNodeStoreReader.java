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
import java.nio.ByteBuffer;
import java.util.Iterator;

import org.neo4j.helpers.UTF8;
import org.neo4j.kernel.impl.nioneo.store.NodeRecord;

public class StubbedNodeStoreReader implements NodeStoreReader
{
    public static final String FROM_VERSION = "NodeStore v0.9.9";
    public static final int RECORD_LENGTH = 9;

    private final long maxId;
    private BufferedInputStream inputStream;

    public StubbedNodeStoreReader( String fileName ) throws IOException
    {
        inputStream = new BufferedInputStream( new FileInputStream( fileName ) );
        int endHeaderSize = UTF8.encode( FROM_VERSION ).length;
        maxId = (new File( fileName ).length() - endHeaderSize) / RECORD_LENGTH;
    }

    public long getMaxId()
    {
        return maxId;
    }

    public Iterable<NodeRecord> readNodeStore() throws IOException
    {
        byte[] buffer = new byte[1024];
        int chunk, total = 0;
        while ( ( chunk = inputStream.read( buffer ) ) != -1 ) {
            total += chunk;
        }
//        ByteBuffer.wrap(  )

        return new Iterable<NodeRecord>()
        {
            @Override
            public Iterator<NodeRecord> iterator()
            {
                return new Iterator()
                {

                    long id = 0;

                    @Override
                    public boolean hasNext()
                    {
                        return id < maxId;
                    }

                    @Override
                    public Object next()
                    {
                        NodeRecord clone = new NodeRecord(id);
                        clone.setNextRel( id );
                        clone.setNextProp( id * 2 );
                        id++;
                        return clone;
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

    public void close() throws IOException
    {
        inputStream.close();
    }
}
