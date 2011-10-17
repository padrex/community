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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import org.junit.Test;
import org.neo4j.kernel.impl.nioneo.store.Buffer;
import org.neo4j.kernel.impl.nioneo.store.OperationType;
import org.neo4j.kernel.impl.nioneo.store.PersistenceWindowPool;
import org.neo4j.kernel.impl.nioneo.store.WindowPool;
import org.neo4j.kernel.impl.storemigration.legacystore.LegacyStore;
import org.neo4j.test.TargetDirectory;

public class ByteReadingMicroBenchmark
{
    public static final int NUMBER_OF_RECORDS = 1000 * 1000;
    public static final int INTEGERS_PER_RECORD = 2;
    public static final int BYTES_PER_INTEGER = 4;
    public static final int TOTAL_BYTES = NUMBER_OF_RECORDS * INTEGERS_PER_RECORD * BYTES_PER_INTEGER;

    public static final int MILLION = (1000 * 1000);

    TargetDirectory targetDirectory = TargetDirectory.forTest( getClass() );

    @Test
    public void shouldReadBytes() throws IOException
    {
        time( new SingleByteBuffer() );
        time( new PersistenceWindow() );
        time( new TotallyMappedWindowPool() );
        time( new BufferedInputStream() );
    }

    private static long nanosToMillis( long nanos )
    {
        return nanos / MILLION;
    }

    private void writeIntegers( File integers ) throws IOException
    {
        OutputStream outputStream = new BufferedOutputStream( new FileOutputStream( integers ) );
        for ( int i = 0; i < NUMBER_OF_RECORDS; i++ )
        {
            for ( int j = 0; j < INTEGERS_PER_RECORD; j++ )
            {
                outputStream.write( 0 );
                outputStream.write( 0 );
                outputStream.write( 0 );
                outputStream.write( 1 );
            }
        }
        outputStream.close();
    }

    public void time( FileProcessor task ) throws IOException
    {
        String taskName = task.getClass().getSimpleName();

        File storeDirectory = targetDirectory.directory( "store-files", true );
        File file = new File( storeDirectory, taskName );

        writeIntegers( file );

        long startTime = System.nanoTime();
        task.run( file );
        long duration = System.nanoTime() - startTime;

        double throughput = TOTAL_BYTES / ((double) duration / (1000 * 1000 * 1000));
        System.out.printf( "%s took %dms, throughput %s%n",
                taskName, nanosToMillis( duration ), formatThroughput( throughput ) );
    }

    private static String formatThroughput( double throughput )
    {
        int thousand = 1024;
        int exponent = (int) (Math.log( throughput ) / Math.log( thousand ));
        char scale = "KMGT".charAt( exponent - 1 );
        return String.format( "%.2f%sB/s", throughput / Math.pow( thousand, exponent ), scale );
    }

    private class SingleByteBuffer implements FileProcessor
    {
        public void run( File file )
        {
            try
            {
                FileChannel fileChannel = new RandomAccessFile( file, "r" ).getChannel();
                MappedByteBuffer byteBuffer = fileChannel.map( FileChannel.MapMode.READ_ONLY, 0, TOTAL_BYTES );
                int sum = 0;
                for ( int i = 0; i < NUMBER_OF_RECORDS; i++ )
                {
                    for ( int j = 0; j < INTEGERS_PER_RECORD; j++ )
                    {
                        sum += byteBuffer.getInt();
                    }
                }
                if ( sum != NUMBER_OF_RECORDS * INTEGERS_PER_RECORD )
                {
                    throw new IllegalStateException( "unexpected sum: " + sum );
                }
                fileChannel.close();
            }
            catch ( IOException e )
            {
                throw new RuntimeException( e );
            }
        }
    }

    private class PersistenceWindow implements FileProcessor
    {
        public void run( File file )
        {
            try
            {
                FileChannel fileChannel = new RandomAccessFile( file, "r" ).getChannel();
                PersistenceWindowPool windowPool = new PersistenceWindowPool( file.getName(),
                        INTEGERS_PER_RECORD * BYTES_PER_INTEGER, fileChannel, MILLION, true, true );

                int sum = 0;
                for ( int i = 0; i < NUMBER_OF_RECORDS; i++ )
                {
                    org.neo4j.kernel.impl.nioneo.store.PersistenceWindow window = windowPool.acquire( i, OperationType.READ );
                    Buffer buffer = window.getOffsettedBuffer( i );
                    for ( int j = 0; j < INTEGERS_PER_RECORD; j++ )
                    {
                        sum += buffer.getInt();
                    }
                    windowPool.release( window );
                }
                if ( sum != NUMBER_OF_RECORDS * INTEGERS_PER_RECORD )
                {
                    throw new IllegalStateException( "unexpected sum: " + sum );
                }
                fileChannel.close();
            }
            catch ( IOException e )
            {
                throw new RuntimeException( e );
            }
        }
    }

    private class TotallyMappedWindowPool implements FileProcessor
    {
        public void run( File file )
        {
            try
            {
                FileChannel fileChannel = new RandomAccessFile( file, "r" ).getChannel();
                WindowPool windowPool = new org.neo4j.kernel.impl.nioneo.store.TotallyMappedWindowPool( file.getName(), 4, fileChannel, MILLION, true, true );

                int sum = 0;
                for ( int i = 0; i < NUMBER_OF_RECORDS; i++ )
                {
                    org.neo4j.kernel.impl.nioneo.store.PersistenceWindow window = windowPool.acquire( i, OperationType.READ );
                    Buffer buffer = window.getOffsettedBuffer( i );
                    for ( int j = 0; j < INTEGERS_PER_RECORD; j++ )
                    {
                        sum += buffer.getInt();
                    }
                    windowPool.release( window );
                }
                if ( sum != NUMBER_OF_RECORDS * INTEGERS_PER_RECORD )
                {
                    throw new IllegalStateException( "unexpected sum: " + sum );
                }
                fileChannel.close();
            }
            catch ( IOException e )
            {
                throw new RuntimeException( e );
            }
        }
    }

    private class BufferedInputStream implements FileProcessor
    {
        public void run( File file )
        {
            try
            {
                java.io.BufferedInputStream inputStream = new java.io.BufferedInputStream( new FileInputStream( file ) );
                int sum = 0;
                byte[] bytes = new byte[INTEGERS_PER_RECORD * BYTES_PER_INTEGER];
                for ( int i = 0; i < NUMBER_OF_RECORDS; i++ )
                {
                    inputStream.read( bytes );
                    for ( int j = 0; j < INTEGERS_PER_RECORD; j++ )
                    {
                        sum += LegacyStore.readUnsignedInt( bytes, j * BYTES_PER_INTEGER );
                    }
                }
                if ( sum != NUMBER_OF_RECORDS * INTEGERS_PER_RECORD )
                {
                    throw new IllegalStateException( "unexpected sum: " + sum );
                }
                inputStream.close();
            }
            catch ( IOException e )
            {
                throw new RuntimeException( e );
            }
        }
    }

    interface FileProcessor
    {

        void run( File file );
    }
}
