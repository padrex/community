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
package org.neo4j.kernel.impl.nioneo.xa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.transaction.xa.XAResource;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.kernel.impl.core.PerTypeChainPosition;
import org.neo4j.kernel.impl.core.PropertyIndex;
import org.neo4j.kernel.impl.core.RelationshipLoadingPosition;
import org.neo4j.kernel.impl.core.SingleChainPosition;
import org.neo4j.kernel.impl.nioneo.store.InvalidRecordException;
import org.neo4j.kernel.impl.nioneo.store.NeoStore;
import org.neo4j.kernel.impl.nioneo.store.NodeRecord;
import org.neo4j.kernel.impl.nioneo.store.NodeState;
import org.neo4j.kernel.impl.nioneo.store.NodeStore;
import org.neo4j.kernel.impl.nioneo.store.PropertyBlock;
import org.neo4j.kernel.impl.nioneo.store.PropertyData;
import org.neo4j.kernel.impl.nioneo.store.PropertyIndexData;
import org.neo4j.kernel.impl.nioneo.store.PropertyIndexRecord;
import org.neo4j.kernel.impl.nioneo.store.PropertyIndexStore;
import org.neo4j.kernel.impl.nioneo.store.PropertyRecord;
import org.neo4j.kernel.impl.nioneo.store.PropertyStore;
import org.neo4j.kernel.impl.nioneo.store.Record;
import org.neo4j.kernel.impl.nioneo.store.RelationshipGroupRecord;
import org.neo4j.kernel.impl.nioneo.store.RelationshipGroupStore;
import org.neo4j.kernel.impl.nioneo.store.RelationshipRecord;
import org.neo4j.kernel.impl.nioneo.store.RelationshipStore;
import org.neo4j.kernel.impl.nioneo.store.RelationshipTypeData;
import org.neo4j.kernel.impl.persistence.NeoStoreTransaction;
import org.neo4j.kernel.impl.transaction.xaframework.XaConnection;
import org.neo4j.kernel.impl.util.ArrayMap;
import org.neo4j.kernel.impl.util.RelIdArray;
import org.neo4j.kernel.impl.util.RelIdArray.DirectionWrapper;

class ReadTransaction implements NeoStoreTransaction
{
    private final NeoStore neoStore;

    public ReadTransaction( NeoStore neoStore )
    {
        this.neoStore = neoStore;
    }

    private NodeStore getNodeStore()
    {
        return neoStore.getNodeStore();
    }

    private int getRelGrabSize()
    {
        return neoStore.getRelationshipGrabSize();
    }

    private RelationshipStore getRelationshipStore()
    {
        return neoStore.getRelationshipStore();
    }

    private RelationshipGroupStore getRelationshipGroupStore()
    {
        return neoStore.getRelationshipGroupStore();
    }
    
    private PropertyStore getPropertyStore()
    {
        return neoStore.getPropertyStore();
    }

    @Override
    public NodeState nodeLoadLight( long nodeId )
    {
        return getNodeStore().loadLightNode( nodeId );
    }

    @Override
    public RelationshipRecord relLoadLight( long id )
    {
        return getRelationshipStore().getLightRel( id );
    }

    @Override
    public RelationshipLoadingPosition getRelationshipChainPosition( long nodeId )
    {
        return getRelationshipChainPosition( nodeId, neoStore );
    }
    
    static RelationshipLoadingPosition getRelationshipChainPosition( long nodeId, NeoStore neoStore )
    {
        NodeRecord node = neoStore.getNodeStore().getRecord( nodeId );
        if ( node.isSuperNode() )
        {
            return new PerTypeChainPosition( loadRelationshipGroups( node, neoStore.getRelationshipGroupStore() ) );
        }
        else
        {
            return new SingleChainPosition( node.getNextRel() );
        }
    }

    static Map<Integer, RelationshipGroupRecord> loadRelationshipGroups( NodeRecord node, RelationshipGroupStore groupStore )
    {
        assert node.isSuperNode();
        long groupId = node.getNextRel();
        Map<Integer, RelationshipGroupRecord> result = new HashMap<Integer, RelationshipGroupRecord>();
        while ( groupId != Record.NO_NEXT_RELATIONSHIP.intValue() )
        {
            RelationshipGroupRecord record = groupStore.getRecord( groupId );
            result.put( record.getType(), record );
            groupId = record.getNext();
        }
        return result;
    }
    
    @Override
    public Map<DirectionWrapper, Iterable<RelationshipRecord>> getMoreRelationships( long nodeId,
            RelationshipLoadingPosition position, RelationshipType[] types )
    {
        return getMoreRelationships( nodeId, position, getRelGrabSize(), getRelationshipStore(), types );
    }

    static Map<DirectionWrapper, Iterable<RelationshipRecord>> getMoreRelationships(
            long nodeId, RelationshipLoadingPosition loadPosition, int grabSize, RelationshipStore relStore,
            RelationshipType... types )
    {
        // initialCapacity=grabSize saves the lists the trouble of resizing
        List<RelationshipRecord> out = new ArrayList<RelationshipRecord>();
        List<RelationshipRecord> in = new ArrayList<RelationshipRecord>();
        List<RelationshipRecord> loop = null;
        Map<DirectionWrapper, Iterable<RelationshipRecord>> result =
            new EnumMap<DirectionWrapper, Iterable<RelationshipRecord>>( DirectionWrapper.class );
        result.put( DirectionWrapper.OUTGOING, out );
        result.put( DirectionWrapper.INCOMING, in );
        long position = loadPosition.position( types );
        for ( int i = 0; i < grabSize &&
            position != Record.NO_NEXT_RELATIONSHIP.intValue(); i++ )
        {
            RelationshipRecord relRecord = relStore.getChainRecord( position );
            if ( relRecord == null )
            {
                // return what we got so far
                return result;
            }
            long firstNode = relRecord.getFirstNode();
            long secondNode = relRecord.getSecondNode();
            if ( relRecord.inUse() )
            {
                if ( firstNode == secondNode )
                {
                    if ( loop == null )
                    {
                        // This is done lazily because loops are probably quite
                        // rarely encountered
                        loop = new ArrayList<RelationshipRecord>();
                        result.put( DirectionWrapper.BOTH, loop );
                    }
                    loop.add( relRecord );
                }
                else if ( firstNode == nodeId )
                {
                    out.add( relRecord );
                }
                else if ( secondNode == nodeId )
                {
                    in.add( relRecord );
                }
            }
            else
            {
                i--;
            }

            long next = 0;
            if ( firstNode == nodeId )
            {
                next = relRecord.getFirstNextRel();
            }
            else if ( secondNode == nodeId )
            {
                next = relRecord.getSecondNextRel();
            }
            else
            {
                throw new InvalidRecordException( "Node[" + nodeId +
                    "] is neither firstNode[" + firstNode +
                    "] nor secondNode[" + secondNode + "] for Relationship[" + relRecord.getId() + "]" );
            }
            position = loadPosition.nextPosition( next, types );
        }
        return result;
    }

    static List<PropertyRecord> getPropertyRecordChain(
            PropertyStore propertyStore, long nextProp )
    {
        List<PropertyRecord> toReturn = new LinkedList<PropertyRecord>();
        if ( nextProp == Record.NO_NEXT_PROPERTY.intValue() )
        {
            return null;
        }
        while ( nextProp != Record.NO_NEXT_PROPERTY.intValue() )
        {
            PropertyRecord propRecord = propertyStore.getLightRecord( nextProp );
            toReturn.add(propRecord);
            nextProp = propRecord.getNextProp();
        }
        return toReturn;
    }

    static ArrayMap<Integer, PropertyData> propertyChainToMap(
            Collection<PropertyRecord> chain )
    {
        if ( chain == null )
        {
            return null;
        }
        ArrayMap<Integer, PropertyData> propertyMap = new ArrayMap<Integer, PropertyData>(
                chain.size(), false, true );
        for ( PropertyRecord propRecord : chain )
        {
            for ( PropertyBlock propBlock : propRecord.getPropertyBlocks() )
            {
                propertyMap.put( propBlock.getKeyIndexId(),
                        propBlock.newPropertyData( propRecord ) );
            }
        }
        return propertyMap;
    }

    static ArrayMap<Integer, PropertyData> loadProperties(
            PropertyStore propertyStore, long nextProp )
    {
        Collection<PropertyRecord> chain = getPropertyRecordChain(
                propertyStore, nextProp );
        if ( chain == null )
        {
            return null;
        }
        return propertyChainToMap( chain );
    }

    @Override
    public ArrayMap<Integer,PropertyData> relLoadProperties( long relId, boolean light )
    {
        RelationshipRecord relRecord = getRelationshipStore().getRecord( relId );
        if ( !relRecord.inUse() )
        {
            throw new InvalidRecordException( "Relationship[" + relId +
                "] not in use" );
        }
        return loadProperties( getPropertyStore(), relRecord.getNextProp() );
    }

    @Override
    public ArrayMap<Integer,PropertyData> nodeLoadProperties( long nodeId, boolean light )
    {
        return loadProperties( getPropertyStore(), getNodeStore().getRecord( nodeId ).getNextProp() );
    }

    // Duplicated code
    public Object propertyGetValueOrNull( PropertyBlock propertyBlock )
    {
        return propertyBlock.getType().getValue( propertyBlock, null );
    }

    @Override
    public Object loadPropertyValue( PropertyData property )
    {
        PropertyRecord propertyRecord = getPropertyStore().getRecord(
                property.getId() );
        PropertyBlock propertyBlock = propertyRecord.getPropertyBlock( property.getIndex() );
        if ( propertyBlock.isLight() )
        {
            getPropertyStore().makeHeavy( propertyBlock );
        }
        return propertyBlock.getType().getValue( propertyBlock,
                getPropertyStore() );
    }

    @Override
    public String loadIndex( int id )
    {
        PropertyIndexStore indexStore = getPropertyStore().getIndexStore();
        PropertyIndexRecord index = indexStore.getRecord( id );
        if ( index.isLight() )
        {
            indexStore.makeHeavy( index );
        }
        return indexStore.getStringFor( index );
    }

    @Override
    public PropertyIndexData[] loadPropertyIndexes( int count )
    {
        PropertyIndexStore indexStore = getPropertyStore().getIndexStore();
        return indexStore.getPropertyIndexes( count );
    }

    /*
        @Override
        public int getKeyIdForProperty( long propertyId )
        {
            PropertyRecord propRecord =
                getPropertyStore().getLightRecord( propertyId );
            return propRecord.getKeyIndexId();
        }
    */
    @Override
    public void setXaConnection( XaConnection connection )
    {
    }

    @Override
    public XAResource getXAResource()
    {
        throw readOnlyException();
    }

    private IllegalStateException readOnlyException()
    {
        return new IllegalStateException(
                "This is a read only transaction, " +
                "this method should never be invoked" );
    }

    @Override
    public void destroy()
    {
        throw readOnlyException();
    }

    @Override
    public ArrayMap<Integer, PropertyData> nodeDelete( long nodeId )
    {
        throw readOnlyException();
    }

    @Override
    public PropertyData nodeAddProperty( long nodeId, PropertyIndex index, Object value )
    {
        throw readOnlyException();
    }

    @Override
    public PropertyData nodeChangeProperty( long nodeId, PropertyData data,
            Object value )
    {
        throw readOnlyException();
    }

    @Override
    public void nodeRemoveProperty( long nodeId, PropertyData data )
    {
        throw readOnlyException();
    }

    @Override
    public void nodeCreate( long id )
    {
        throw readOnlyException();
    }

    @Override
    public void relationshipCreate( long id, int typeId, long startNodeId, long endNodeId )
    {
        throw readOnlyException();
    }

    @Override
    public ArrayMap<Integer, PropertyData> relDelete( long relId )
    {
        throw readOnlyException();
    }

    @Override
    public PropertyData relAddProperty( long relId, PropertyIndex index, Object value )
    {
        throw readOnlyException();
    }

    @Override
    public PropertyData relChangeProperty( long relId, PropertyData data,
            Object value )
    {
        throw readOnlyException();
    }

    @Override
    public void relRemoveProperty( long relId, PropertyData data )
    {
        throw readOnlyException();
    }

    @Override
    public RelationshipTypeData[] loadRelationshipTypes()
    {
        RelationshipTypeData relTypeData[] =
            neoStore.getRelationshipTypeStore().getRelationshipTypes();
        RelationshipTypeData rawRelTypeData[] =
            new RelationshipTypeData[relTypeData.length];
        for ( int i = 0; i < relTypeData.length; i++ )
        {
            rawRelTypeData[i] = new RelationshipTypeData(
                relTypeData[i].getId(), relTypeData[i].getName() );
        }
        return rawRelTypeData;
    }

    @Override
    public void createPropertyIndex( String key, int id )
    {
        throw readOnlyException();
    }

    @Override
    public void createRelationshipType( int id, String name )
    {
        throw readOnlyException();
    }

    @Override
    public RelIdArray getCreatedNodes()
    {
        return RelIdArray.EMPTY;
    }

    @Override
    public boolean isNodeCreated( long nodeId )
    {
        return false;
    }

    @Override
    public boolean isRelationshipCreated( long relId )
    {
        return false;
    }

    public static int getKeyIdForProperty( PropertyData property,
            PropertyStore store )
    {
        // PropertyRecord propRecord = store.getLightRecord( property.getId() );
        // return propRecord.getKeyIndexIds();
        return property.getIndex();
    }

    @Override
    public int getKeyIdForProperty( PropertyData property )
    {
        return getKeyIdForProperty( property, getPropertyStore() );
    }
    
    @Override
    public int getRelationshipCount( long id, int type, Direction direction )
    {
        NodeRecord node = getNodeStore().getRecord( id );
        long nextRel = node.getNextRel();
        if ( nextRel == Record.NO_NEXT_RELATIONSHIP.intValue() ) return 0;
        if ( !node.isSuperNode() )
        {
            assert type == -1;
            assert direction == null;
            return getRelationshipCount( node, nextRel );
        }
        else
        {
            Map<Integer, RelationshipGroupRecord> groups = loadRelationshipGroups( node, getRelationshipGroupStore() );
            if ( type == -1 && direction == null )
            {   // Count for all types/directions
                int count = 0;
                for ( RelationshipGroupRecord group : groups.values() )
                {
                    count += getRelationshipCount( node, group.getNextOut() );
                    count += getRelationshipCount( node, group.getNextIn() );
                    count += getRelationshipCount( node, group.getNextLoop() );
                }
                return count;
            }
            else if ( type == -1 )
            {   // Count for all types with a given direction
                int count = 0;
                for ( RelationshipGroupRecord group : groups.values() )
                {
                    count += getRelationshipCount( node, group, direction );
                }
                return count;
            }
            else if ( direction == null )
            {   // Count for a type
                RelationshipGroupRecord group = groups.get( type );
                if ( group == null ) return 0;
                int count = 0;
                count += getRelationshipCount( node, group.getNextOut() );
                count += getRelationshipCount( node, group.getNextIn() );
                count += getRelationshipCount( node, group.getNextLoop() );
                return count;
            }
            else
            {   // Count for one type and direction
                RelationshipGroupRecord group = groups.get( type );
                if ( group == null ) return 0;
                return getRelationshipCount( node, group, direction );
            }
        }
    }
    
    private int getRelationshipCount( NodeRecord node, RelationshipGroupRecord group, Direction direction )
    {
        long relId = -1;
        switch ( direction )
        {
        case OUTGOING: relId = group.getNextOut(); break;
        case INCOMING: relId = group.getNextIn(); break;
        default: relId = group.getNextLoop(); break;
        }
        return getRelationshipCount( node, relId );
    }

    private int getRelationshipCount( NodeRecord node, long relId )
    {
        if ( relId == Record.NO_NEXT_RELATIONSHIP.intValue() ) return 0;
        RelationshipRecord rel = getRelationshipStore().getRecord( relId );
        return (int) (node.getId() == rel.getFirstNode() ? rel.getFirstPrevRel() : rel.getSecondPrevRel());
    }
}
