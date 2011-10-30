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

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.kernel.impl.nioneo.store.PropertyData;
import org.neo4j.kernel.impl.util.ArrayMap;

public class GraphProperties extends Primitive implements PropertyContainer
{
    private final Object LOCK = new Object();
    
    private final NodeManager nodeManager;
    
    // TODO Primitive have a PropertyData[], make this use a Map instead
//    private final ArrayMap<Integer, PropertyData> properties;
    
    GraphProperties( NodeManager nodeManager, long startRecord )
    {
        super( false );
        this.nodeManager = nodeManager;
//        this.properties = cacheProperties( startRecord );
    }
    
//    private ArrayMap<Integer, PropertyData> cacheProperties( long startRecord )
//    {
//        return nodeManager.getPersistenceManager().loadProperties( startRecord, false );
//    }

    @Override
    public GraphDatabaseService getGraphDatabase()
    {
        return this.nodeManager.getGraphDbService();
    }

    @Override
    protected PropertyData changeProperty( NodeManager nodeManager, PropertyData property, Object value )
    {
        return nodeManager.graphChangeProperty( property, value );
    }

    @Override
    protected PropertyData addProperty( NodeManager nodeManager, PropertyIndex index, Object value )
    {
        return nodeManager.graphAddProperty( index, value );
    }

    @Override
    protected void removeProperty( NodeManager nodeManager, PropertyData property )
    {
        nodeManager.graphRemoveProperty( property );
    }

    @Override
    protected ArrayMap<Integer, PropertyData> loadProperties( NodeManager nodeManager, boolean light )
    {
        return nodeManager.loadGraphProperties( light );
    }

    @Override
    public long getId()
    {
        return -1L;
    }

    @Override
    public boolean hasProperty( String key )
    {
        return hasProperty( nodeManager, key );
    }

    @Override
    public Object getProperty( String key )
    {
        return getProperty( nodeManager, key );
    }

    @Override
    public Object getProperty( String key, Object defaultValue )
    {
        return getProperty( nodeManager, key, defaultValue );
    }

    @Override
    public void setProperty( String key, Object value )
    {
        setProperty( nodeManager, key, value );
    }

    @Override
    public Object removeProperty( String key )
    {
        return removeProperty( nodeManager, key );
    }

    @Override
    public Iterable<String> getPropertyKeys()
    {
        return getPropertyKeys( nodeManager );
    }

    @Override
    public Iterable<Object> getPropertyValues()
    {
        return getPropertyValues( nodeManager );
    }
}
