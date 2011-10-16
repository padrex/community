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
package org.neo4j.kernel.impl.storemigration;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.junit.Test;
import org.neo4j.kernel.impl.nioneo.store.NeoStore;
import org.neo4j.kernel.impl.storemigration.legacystore.LegacyReaderFactory;
import org.neo4j.kernel.impl.storemigration.legacystore.LegacyStore;
import org.neo4j.kernel.impl.storemigration.legacystore.SingleByteBufferReaderFactory;
import org.neo4j.kernel.impl.storemigration.monitoring.VisibleMigrationProgressMonitor;
import org.neo4j.kernel.impl.util.FileUtils;

public class StoreMigratorSpeedBenchmark
{
    @Test
    public void shouldMigrate() throws IOException
    {
//        URL legacyStoreResource = getClass().getResource( "legacystore/exampledb/neostore" );
//        String storageFileName = legacyStoreResource.getFile();
        String storageFileName = "/Users/apcj/projects/neo4j/legacy-store-creator/target/output-database/neostore";

        final LegacyStore warmUpLegacyStore = new LegacyStore( storageFileName, new LegacyReaderFactory() );

        long referenceWarmUp = time( new Runnable()
        {
            public void run()
            {
                migrate( warmUpLegacyStore );
            }
        } );

        final LegacyStore referenceLegacyStore = new LegacyStore( storageFileName, new LegacyReaderFactory() );

        long reference = time( new Runnable()
        {
            public void run()
            {
                migrate( referenceLegacyStore );
            }
        } );

        final LegacyStore trialLegacyStore = new LegacyStore( storageFileName, new SingleByteBufferReaderFactory() );

        long trial = time( new Runnable()
        {
            public void run()
            {
                migrate( trialLegacyStore );
            }
        } );

        System.out.printf( "referenceWarmUp = %ds%n", referenceWarmUp / 1000 );
        System.out.printf( "reference = %ds%n", reference / 1000 );
        System.out.printf( "trial = %ds%n", trial / 1000 );
        System.out.println( String.format( "Trial is %f%% faster than reference", (reference - trial) / (double) reference * 100 ) );
    }

    private long time( Runnable runnable )
    {
        long startTime = System.currentTimeMillis();
        runnable.run();
        return System.currentTimeMillis() - startTime;
    }

    private void migrate( LegacyStore legacyStore )
    {
        try
        {
            HashMap config = MigrationTestUtils.defaultConfig();
            File outputDir = new File( "target/outputDatabase" );
            FileUtils.deleteRecursively( outputDir );
            assertTrue( outputDir.mkdirs() );

            String storeFileName = "target/outputDatabase/neostore";
            config.put( "neo_store", storeFileName );
            NeoStore.createStore( storeFileName, config );
            NeoStore neoStore = new NeoStore( config );

            new StoreMigrator( new VisibleMigrationProgressMonitor(System.out) ).migrate( legacyStore, neoStore );
            neoStore.close();
            legacyStore.close();
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }
}
