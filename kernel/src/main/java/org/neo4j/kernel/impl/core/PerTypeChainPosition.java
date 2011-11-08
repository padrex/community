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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.helpers.collection.ArrayIterator;
import org.neo4j.kernel.impl.nioneo.store.Record;
import org.neo4j.kernel.impl.nioneo.store.RelationshipGroupRecord;

public class PerTypeChainPosition implements RelationshipLoadingPosition
{
    private final Map<String, TypePosition> positions = new HashMap<String, TypePosition>();
    private final Map<Integer, RelationshipGroupRecord> rawGroups;
    private Map<String, RelationshipGroupRecord> groups;
    private RelationshipType[] types;
    
    public PerTypeChainPosition( Map<Integer, RelationshipGroupRecord> groups )
    {
        this.rawGroups = groups;
    }
    
    @Override
    public void setNodeManager( NodeManager nodeManager )
    {
        groups = new HashMap<String, RelationshipGroupRecord>();
        types = new RelationshipType[rawGroups.size()];
        int i = 0;
        for ( Map.Entry<Integer, RelationshipGroupRecord> entry : rawGroups.entrySet() )
        {
            RelationshipType type = nodeManager.getRelationshipTypeById( entry.getKey() );
            groups.put( type.name(), entry.getValue() );
            types[i++] = type;
        }
    }
    
    @Override
    public long position( RelationshipType... types )
    {
        if ( types.length == 0 ) types = this.types;
        for ( RelationshipType type : types )
        {
            TypePosition position = getTypePosition( type );
            if ( !position.end ) return position.position( types );
        }
        return Record.NO_NEXT_RELATIONSHIP.intValue();
    }

    private TypePosition getTypePosition( RelationshipType type )
    {
        TypePosition position = positions.get( type.name() );
        if ( position == null )
        {
            RelationshipGroupRecord record = groups.get( type.name() );
            assert record != null;
            position = new TypePosition( record );
            positions.put( type.name(), position );
        }
        return position;
    }

    @Override
    public long nextPosition( long nextPosition, RelationshipType... types )
    {
        if ( types.length == 0 ) types = this.types;
        for ( RelationshipType type : types )
        {
            TypePosition position = getTypePosition( type );
            if ( !position.end )
            {
                long result = position.nextPosition( nextPosition );
                if ( !position.end ) return result;
            }
        }
        return Record.NO_NEXT_RELATIONSHIP.intValue();
    }
    
    @Override
    public boolean hasMore( RelationshipType... types )
    {
        if ( types.length == 0 ) types = this.types;
        for ( RelationshipType type : types )
        {
            TypePosition position = positions.get( type.name() );
            if ( position == null || position.hasMore() ) return true;
        }
        return false;
    }
    
    private static class TypePosition implements RelationshipLoadingPosition
    {
        private final Iterator<Direction> directions = new ArrayIterator<Direction>( Direction.values() );
        private Direction direction = null;
        private RelationshipGroupRecord record;
        private long position = Record.NO_NEXT_RELATIONSHIP.intValue();
        private boolean end;
        
        TypePosition( RelationshipGroupRecord record )
        {
            this.record = record;
        }
        
        @Override
        public void setNodeManager( NodeManager nodeManager )
        {
            throw new UnsupportedOperationException();
        }

        private long gotoNextPosition()
        {
            while ( directions.hasNext() )
            {
                direction = directions.next();
                this.position = positionForDirection( direction );
                if ( this.position != Record.NO_NEXT_RELATIONSHIP.intValue() ) return this.position;
            }
            end = true;
            return this.position;
        }

        private long positionForDirection( Direction direction )
        {
            switch ( direction )
            {
            case OUTGOING: return record.getNextOut();
            case INCOMING: return record.getNextIn();
            default: return record.getNextLoop();
            }
        }

        @Override
        public long position( RelationshipType... types )
        {
            assert !end;
            if ( position == Record.NO_NEXT_RELATIONSHIP.intValue() ) gotoNextPosition();
            return position;
        }

        @Override
        public long nextPosition( long position, RelationshipType... types )
        {
            if ( position != Record.NO_NEXT_RELATIONSHIP.intValue() )
            {
                this.position = position;
                return position;
            }
            return gotoNextPosition();
        }
        
        @Override
        public boolean hasMore( RelationshipType... types )
        {
            return !end;
        }
    }
}
