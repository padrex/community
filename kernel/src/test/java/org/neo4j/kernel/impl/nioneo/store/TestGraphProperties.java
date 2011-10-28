package org.neo4j.kernel.impl.nioneo.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
        commitTx();
        assertEquals( "yo", properties().getProperty( "test" ) );
    }

    private void commitTx()
    {
        if ( tx == null ) throw new IllegalStateException( "Transaction not started" );
        tx.success();
        tx.finish();
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
