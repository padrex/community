package org.neo4j.kernel.impl.storemigration.legacystore;

import java.io.IOException;

import org.neo4j.kernel.impl.nioneo.store.NodeRecord;

public interface NodeStoreReader
{
    long getMaxId();

    Iterable<NodeRecord> readNodeStore() throws IOException;

    void close() throws IOException;
}
