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
package org.neo4j.kernel.impl.core;

import static org.junit.Assert.assertEquals;
import static org.neo4j.helpers.collection.IteratorUtil.addToCollection;
import static org.neo4j.helpers.collection.MapUtil.stringMap;
import static org.neo4j.test.TargetDirectory.forTest;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.Config;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.impl.MyRelTypes;

public class TestSuperNodes
{
    private static EmbeddedGraphDatabase db;
    
    @BeforeClass
    public static void doBefore() throws Exception
    {
        db = new EmbeddedGraphDatabase( forTest( TestSuperNodes.class ).graphDbDir( true ).getAbsolutePath(),
                stringMap( "super_node_threshold", "5", Config.KEEP_LOGICAL_LOGS, "true" ) );
    }
    
    @AfterClass
    public static void doAfter() throws Exception
    {
        db.shutdown();
    }
    
    @After
    public void afterTest() throws Exception
    {
        if ( tx != null ) commitTx();
    }
    
    private Transaction tx;
    
    protected void beginTx()
    {
        assert tx == null;
        tx = db.beginTx();
    }
    
    protected void finishTx( boolean success )
    {
        assert tx != null;
        if ( success ) tx.success();
        tx.finish();
    }
    
    protected void commitTx()
    {
        finishTx( true );
    }
    
    protected void restartTx()
    {
        commitTx();
        beginTx();
    }
    
    protected void clearCache()
    {
        db.getConfig().getGraphDbModule().getNodeManager().clearCache();
    }
    
    @Test
    public void convertToSuperNode() throws Exception
    {
        beginTx();
        Node node = db.createNode();
        EnumMap<MyRelTypes, Set<Relationship>> rels = new EnumMap<MyRelTypes, Set<Relationship>>( MyRelTypes.class );
        for ( MyRelTypes type : MyRelTypes.values() ) rels.put( type, new HashSet<Relationship>() );
        for ( int i = 0; i < 6; i++ )
        {
            MyRelTypes type = MyRelTypes.values()[i%MyRelTypes.values().length];
            Relationship rel = node.createRelationshipTo( db.createNode(), type );
            rels.get( type ).add( rel );
        }
        restartTx();
        for ( int i = 0; i < 100000; i++ )
        {
            node.createRelationshipTo( db.createNode(), MyRelTypes.TEST );
            if ( i%10000 == 0 && i > 0 ) restartTx();
        }
        clearCache();
        System.out.println( "there" );
        assertEquals( rels.get( MyRelTypes.TEST2 ),
                addToCollection( node.getRelationships( MyRelTypes.TEST2 ), new HashSet<Relationship>() ) );
        assertEquals( join( rels.get( MyRelTypes.TEST_TRAVERSAL ), rels.get( MyRelTypes.TEST2 ) ),
                addToCollection( node.getRelationships( MyRelTypes.TEST_TRAVERSAL, MyRelTypes.TEST2 ), new HashSet<Relationship>() ) );
    }

    private <T> Set<T> join( Set<T>... sets )
    {
        Set<T> result = new HashSet<T>();
        for ( Set<T> set : sets ) result.addAll( set );
        return result;
    }
}
