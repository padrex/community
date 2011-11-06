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

import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.kernel.impl.AbstractNeo4jTestCase;
import org.neo4j.kernel.impl.MyRelTypes;

public class TestRelationshipCount extends AbstractNeo4jTestCase
{
    @Test
    public void simple() throws Exception
    {
        Node node1 = getGraphDb().createNode();
        Node node2 = getGraphDb().createNode();
        assertEquals( 0, node1.getRelationshipCount() );
        assertEquals( 0, node2.getRelationshipCount() );
        node1.createRelationshipTo( node2, MyRelTypes.TEST );
        assertEquals( 1, node1.getRelationshipCount() );
        assertEquals( 1, node2.getRelationshipCount() );
        node1.createRelationshipTo( getGraphDb().createNode(), MyRelTypes.TEST2 );
        assertEquals( 2, node1.getRelationshipCount() );
        assertEquals( 1, node2.getRelationshipCount() );
        newTransaction();
        assertEquals( 2, node1.getRelationshipCount() );
        assertEquals( 1, node2.getRelationshipCount() );

        for ( int i = 0; i < 1000; i++ ) 
        {
            if ( i%2 == 0 ) node1.createRelationshipTo( node2, MyRelTypes.TEST );
            else node2.createRelationshipTo( node1, MyRelTypes.TEST );
            assertEquals( i+2+1, node1.getRelationshipCount() );
            assertEquals( i+1+1, node2.getRelationshipCount() );
            if ( i%10 == 0 )
            {
                newTransaction();
                clearCache();
            }
        }
    }
    
    @Test
    public void withLoops() throws Exception
    {
        // Just to make sure it doesn't work by accident what with ids aligning with count
        for ( int i = 0; i < 10; i++ ) getGraphDb().createNode().createRelationshipTo( getGraphDb().createNode(), MyRelTypes.TEST );
        
        Node node = getGraphDb().createNode();
        assertEquals( 0, node.getRelationshipCount() );
        Relationship rel1 = node.createRelationshipTo( node, MyRelTypes.TEST );
        assertEquals( 1, node.getRelationshipCount() );
        Node otherNode = getGraphDb().createNode();
        Relationship rel2 = node.createRelationshipTo( otherNode, MyRelTypes.TEST2 );
        assertEquals( 2, node.getRelationshipCount() );
        assertEquals( 1, otherNode.getRelationshipCount() );
        newTransaction();
        assertEquals( 2, node.getRelationshipCount() );
        Relationship rel3 = node.createRelationshipTo( node, MyRelTypes.TEST_TRAVERSAL );
        assertEquals( 3, node.getRelationshipCount() );
        assertEquals( 1, otherNode.getRelationshipCount() );
        rel2.delete();
        assertEquals( 2, node.getRelationshipCount() );
        assertEquals( 0, otherNode.getRelationshipCount() );
        rel3.delete();
        assertEquals( 1, node.getRelationshipCount() );
    }
}
