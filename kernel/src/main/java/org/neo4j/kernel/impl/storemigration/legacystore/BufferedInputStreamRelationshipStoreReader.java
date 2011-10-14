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
import org.neo4j.kernel.impl.nioneo.store.Record;
import org.neo4j.kernel.impl.nioneo.store.RelationshipRecord;

public class BufferedInputStreamRelationshipStoreReader implements RelationshipStoreReader
{
    public static final String FROM_VERSION = "RelationshipStore v0.9.9";
    public static final int RECORD_LENGTH = 33;

    private final long maxId;
    private BufferedInputStream inputStream;

    public BufferedInputStreamRelationshipStoreReader( String fileName ) throws IOException
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
    public Iterable<RelationshipRecord> readRelationshipStore() throws IOException
    {
        final byte[] bytes = new byte[RECORD_LENGTH];

        return new Iterable<RelationshipRecord>()
        {
            @Override
            public Iterator<RelationshipRecord> iterator()
            {
                return new PrefetchingIterator<RelationshipRecord>()
                {
                    long id = 0;

                    @Override
                    protected RelationshipRecord fetchNextOrNull()
                    {
                        RelationshipRecord record = null;
                        while ( record == null && id <= maxId )
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
                            if ( inUse )
                            {
                                long firstNode = LegacyStore.readUnsignedInt( bytes, 1 );
                                long firstNodeMod = (inUseByte & 0xEL) << 31;

                                long secondNode = LegacyStore.readUnsignedInt( bytes, 5 );

                                // [ xxx,    ][    ,    ][    ,    ][    ,    ] second node high order bits,     0x70000000
                                // [    ,xxx ][    ,    ][    ,    ][    ,    ] first prev rel high order bits,  0xE000000
                                // [    ,   x][xx  ,    ][    ,    ][    ,    ] first next rel high order bits,  0x1C00000
                                // [    ,    ][  xx,x   ][    ,    ][    ,    ] second prev rel high order bits, 0x380000
                                // [    ,    ][    , xxx][    ,    ][    ,    ] second next rel high order bits, 0x70000
                                // [    ,    ][    ,    ][xxxx,xxxx][xxxx,xxxx] type
                                long typeInt = LegacyStore.readUnsignedInt( bytes, 9 );
                                long secondNodeMod = (typeInt & 0x70000000L) << 4;
                                int type = (int) (typeInt & 0xFFFF);

                                record = new RelationshipRecord( id,
                                        LegacyStore.longFromIntAndMod( firstNode, firstNodeMod ),
                                        LegacyStore.longFromIntAndMod( secondNode, secondNodeMod ), type );
                                record.setInUse( inUse );

                                long firstPrevRel = LegacyStore.readUnsignedInt( bytes, 13 );
                                long firstPrevRelMod = (typeInt & 0xE000000L) << 7;
                                record.setFirstPrevRel( LegacyStore.longFromIntAndMod( firstPrevRel, firstPrevRelMod ) );

                                long firstNextRel = LegacyStore.readUnsignedInt( bytes, 17 );
                                long firstNextRelMod = (typeInt & 0x1C00000L) << 10;
                                record.setFirstNextRel( LegacyStore.longFromIntAndMod( firstNextRel, firstNextRelMod ) );

                                long secondPrevRel = LegacyStore.readUnsignedInt( bytes, 21 );
                                long secondPrevRelMod = (typeInt & 0x380000L) << 13;
                                record.setSecondPrevRel( LegacyStore.longFromIntAndMod( secondPrevRel, secondPrevRelMod ) );

                                long secondNextRel = LegacyStore.readUnsignedInt( bytes, 25 );
                                long secondNextRelMod = (typeInt & 0x70000L) << 16;
                                record.setSecondNextRel( LegacyStore.longFromIntAndMod( secondNextRel, secondNextRelMod ) );

                                long nextProp = LegacyStore.readUnsignedInt( bytes, 29 );
                                long nextPropMod = (inUseByte & 0xF0L) << 28;

                                record.setNextProp( LegacyStore.longFromIntAndMod( nextProp, nextPropMod ) );
                            }
                            else
                            {
                                record = new RelationshipRecord( id, -1, -1, -1 );
                                record.setInUse( false );
                            }
                            id++;
                        }

                        return record;
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
        inputStream.close();
    }
}
