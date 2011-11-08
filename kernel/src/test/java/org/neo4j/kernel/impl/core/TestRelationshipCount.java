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
import org.neo4j.graphdb.Direction;
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
        assertEquals( 0, node1.getDegree() );
        assertEquals( 0, node2.getDegree() );
        node1.createRelationshipTo( node2, MyRelTypes.TEST );
        assertEquals( 1, node1.getDegree() );
        assertEquals( 1, node2.getDegree() );
        node1.createRelationshipTo( getGraphDb().createNode(), MyRelTypes.TEST2 );
        assertEquals( 2, node1.getDegree() );
        assertEquals( 1, node2.getDegree() );
        newTransaction();
        assertEquals( 2, node1.getDegree() );
        assertEquals( 1, node2.getDegree() );

        for ( int i = 0; i < 1000; i++ ) 
        {
            if ( i%2 == 0 ) node1.createRelationshipTo( node2, MyRelTypes.TEST );
            else node2.createRelationshipTo( node1, MyRelTypes.TEST );
            assertEquals( i+2+1, node1.getDegree() );
            assertEquals( i+1+1, node2.getDegree() );
            if ( i%10 == 0 )
            {
                newTransaction();
                clearCache();
            }
        }
        
        for ( int i = 0; i < 2; i++ )
        {
            assertEquals( 1002, node1.getDegree() );
            assertEquals( 502, node1.getDegree( Direction.OUTGOING ) );
            assertEquals( 500, node1.getDegree( Direction.INCOMING ) );
            assertEquals( 1, node1.getDegree( MyRelTypes.TEST2 ) );
            assertEquals( 1001, node1.getDegree( MyRelTypes.TEST ) );
            assertEquals( 501, node1.getDegree( MyRelTypes.TEST, Direction.OUTGOING ) );
            assertEquals( 500, node1.getDegree( MyRelTypes.TEST, Direction.INCOMING ) );
            assertEquals( 1, node1.getDegree( MyRelTypes.TEST2, Direction.OUTGOING ) );
            assertEquals( 0, node1.getDegree( MyRelTypes.TEST2, Direction.INCOMING ) );
            newTransaction();
        }
    }
    
    @Test
    public void withLoops() throws Exception
    {
        // Just to make sure it doesn't work by accident what with ids aligning with count
        for ( int i = 0; i < 10; i++ ) getGraphDb().createNode().createRelationshipTo( getGraphDb().createNode(), MyRelTypes.TEST );
        
        Node node = getGraphDb().createNode();
        assertEquals( 0, node.getDegree() );
        Relationship rel1 = node.createRelationshipTo( node, MyRelTypes.TEST );
        assertEquals( 1, node.getDegree() );
        Node otherNode = getGraphDb().createNode();
        Relationship rel2 = node.createRelationshipTo( otherNode, MyRelTypes.TEST2 );
        assertEquals( 2, node.getDegree() );
        assertEquals( 1, otherNode.getDegree() );
        newTransaction();
        assertEquals( 2, node.getDegree() );
        Relationship rel3 = node.createRelationshipTo( node, MyRelTypes.TEST_TRAVERSAL );
        assertEquals( 3, node.getDegree() );
        assertEquals( 1, otherNode.getDegree() );
        rel2.delete();
        assertEquals( 2, node.getDegree() );
        assertEquals( 0, otherNode.getDegree() );
        rel3.delete();
        assertEquals( 1, node.getDegree() );
    }
}
