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
package org.neo4j.kernel.impl.nioneo.store;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.neo4j.helpers.collection.IteratorUtil.asCollection;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.test.TargetDirectory;

public class TestGraphProperties
{
    private EmbeddedGraphDatabase db;
    private Transaction tx;

    @Before
    public void doBefore() throws Exception
    {
        db = new EmbeddedGraphDatabase( TargetDirectory.forTest( TestGraphProperties.class ).directory( "db", true ).getAbsolutePath() );
    }
    
    @After
    public void doAfter() throws Exception
    {
        db.shutdown();
    }
    
    @Test
    public void basicProperties() throws Exception
    {
        assertNull( properties().getProperty( "test", null ) );
        beginTx();
        properties().setProperty( "test", "yo" );
        assertEquals( "yo", properties().getProperty( "test" ) );
        finishTx( true );
        assertEquals( "yo", properties().getProperty( "test" ) );
        beginTx();
        assertNull( properties().removeProperty( "something non existent" ) );
        assertEquals( "yo", properties().removeProperty( "test" ) );
        assertNull( properties().getProperty( "test", null ) );
        properties().setProperty( "other", 10 );
        assertEquals( 10, properties().getProperty( "other" ) );
        properties().setProperty( "new", "third" );
        finishTx( true );
        assertNull( properties().getProperty( "test", null ) );
        assertEquals( 10, properties().getProperty( "other" ) );
        assertEquals( asSet( asCollection( properties().getPropertyKeys() ) ), asSet( asList( "other", "new" ) ) );
        
        beginTx();
        properties().setProperty( "rollback", true );
        assertEquals( true, properties().getProperty( "rollback" ) );
        finishTx( false );
        assertNull( properties().getProperty( "rollback", null ) );
    }

    private <T> Set<T> asSet( Collection<T> asCollection )
    {
        Set<T> set = new HashSet<T>();
        set.addAll( asCollection );
        return set;
    }

    private void finishTx( boolean success )
    {
        if ( tx == null ) throw new IllegalStateException( "Transaction not started" );
        if ( success ) tx.success();
        tx.finish();
        tx = null;
    }
    
    private void beginTx()
    {
        if ( tx != null ) throw new IllegalStateException( "Transaction already started" );
        tx = db.beginTx();
    }

    private PropertyContainer properties()
    {
        return db.getConfig().getGraphDbModule().getNodeManager().getGraphProperties();
    }
}
