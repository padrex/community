package org.neo4j.kernel.impl.storemigration.legacystore;

import java.io.IOException;

public class StubbedReaderFactory implements ReaderFactory
{
    @Override
    public NodeStoreReader newLegacyNodeStoreReader( String fileName ) throws IOException
    {
        return new StubbedNodeStoreReader( fileName );
    }
}
