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
package org.neo4j.server.rrd.sampler;

import org.neo4j.server.database.Database;
import org.neo4j.server.rrd.Sampleable;
import org.neo4j.server.statistic.StatisticCollector;
import org.neo4j.server.statistic.StatisticRecord;
import org.rrd4j.DsType;

public abstract class StatisticSampleableBase implements Sampleable
{

    private final StatisticCollector collector;
    private final DsType dsType;

    public StatisticSampleableBase( Database db, DsType dsType )
    {
        collector = db.statisticCollector();
        this.dsType = dsType;
    }


    public abstract String getName();

    public abstract double getValue();

    protected StatisticRecord getCurrentSnapshot()
    {
        return collector.currentSnapshot();
    }

    @Override
    public DsType getType()
    {
        return dsType;
    }
}
