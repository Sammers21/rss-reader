package rss.reader;


import com.datastax.driver.core.PreparedStatement;
import io.vertx.cassandra.CassandraClient;
import io.vertx.core.Future;

import java.util.concurrent.atomic.AtomicReference;

public class Util {

    /**
     * @param client    the client
     * @param query     the query yo prepare
     * @param reference the reference to update after preparation
     * @return reference will be updated as soon as a returned future completes
     */
    public static Future<Void> prepareQueryAndSetReference(CassandraClient client,
                                                           String query,
                                                           AtomicReference<PreparedStatement> reference) {
        Future<PreparedStatement> future = Future.future();
        client.prepare(query, future);
        return future.compose(preparedStatement -> {
            reference.set(preparedStatement);
            return Future.succeededFuture();
        });
    }
}
