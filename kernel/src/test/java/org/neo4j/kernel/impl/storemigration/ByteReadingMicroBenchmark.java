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
import java.io.FileNotFoundException;
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
import org.neo4j.kernel.impl.storemigration.legacystore.LegacyStore;
import org.neo4j.test.TargetDirectory;

public class ByteReadingMicroBenchmark
{

    public static final int NUMBER_OF_INTEGERS = 4 * 1000 * 1000;
    public static final int MILLION = (1000 * 1000);

    @Test
    public void shouldReadBytes() throws IOException
    {
        TargetDirectory target = TargetDirectory.forTest( getClass() );
        File storeDirectory = target.directory( "store-files", true );
        File file = new File( storeDirectory, "integers" );

        writeIntegers( file );

        long singleByteBuffer = time( new SingleByteBuffer( file ) );
        System.out.println( "singleByteBuffer = " + nanosToMillis( singleByteBuffer ) );

        long persistenceWindow = time( new PersistenceWindow( file ) );
        System.out.println( "persistenceWindow = " + nanosToMillis( persistenceWindow ) );

        long bufferedInputStream = time( new BufferedInputStream( file ) );
        System.out.println( "bufferedInputStream = " + nanosToMillis( bufferedInputStream ) );
    }

    private long nanosToMillis( long nanos )
    {
        return nanos / MILLION;
    }

    private void writeIntegers( File integers ) throws IOException
    {
        OutputStream outputStream = new BufferedOutputStream( new FileOutputStream( integers ) );
        for ( int i = 0; i < NUMBER_OF_INTEGERS; i++ )
        {
            outputStream.write( 0 );
            outputStream.write( 0 );
            outputStream.write( 0 );
            outputStream.write( 1 );
        }
        outputStream.close();
    }

    public static long time( Runnable task )
    {
        long startTime = System.nanoTime();
        task.run();
        return System.nanoTime() - startTime;
    }

    private class SingleByteBuffer implements Runnable
    {
        private File file;

        public SingleByteBuffer( File file ) throws FileNotFoundException
        {
            this.file = file;
        }

        public void run()
        {
            try
            {
                FileChannel fileChannel = new RandomAccessFile( file, "r" ).getChannel();
                MappedByteBuffer byteBuffer = fileChannel.map( FileChannel.MapMode.READ_ONLY, 0, NUMBER_OF_INTEGERS * 4 );
                int sum = 0;
                for ( int i = 0; i < NUMBER_OF_INTEGERS; i++ )
                {
                    sum += byteBuffer.getInt();
                }
                if ( sum != NUMBER_OF_INTEGERS )
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

    private class PersistenceWindow implements Runnable
    {
        private File file;

        public PersistenceWindow( File file ) throws FileNotFoundException
        {
            this.file = file;
        }

        public void run()
        {
            try
            {
                FileChannel fileChannel = new RandomAccessFile( file, "r" ).getChannel();
                PersistenceWindowPool windowPool = new PersistenceWindowPool( file.getName(), 4, fileChannel, MILLION, true, true );

                int sum = 0;
                for ( int i = 0; i < NUMBER_OF_INTEGERS; i++ )
                {
                    org.neo4j.kernel.impl.nioneo.store.PersistenceWindow window = windowPool.acquire( i, OperationType.READ );
                    Buffer buffer = window.getOffsettedBuffer( i );
                    sum += buffer.getInt();
                    windowPool.release( window );
                }
                if ( sum != NUMBER_OF_INTEGERS )
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

    private class BufferedInputStream implements Runnable
    {
        private File file;

        public BufferedInputStream( File file ) throws FileNotFoundException
        {
            this.file = file;
        }

        public void run()
        {
            try
            {
                java.io.BufferedInputStream inputStream = new java.io.BufferedInputStream( new FileInputStream( file ) );
                int sum = 0;
                byte[] bytes = new byte[4];
                for ( int i = 0; i < NUMBER_OF_INTEGERS; i++ )
                {
                    inputStream.read( bytes );
                    sum += LegacyStore.readUnsignedInt( bytes, 0 );
                }
                if ( sum != NUMBER_OF_INTEGERS )
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
}
