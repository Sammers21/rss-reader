/*
 * Copyright 2018 The Vert.x Community.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rss.reader;

import com.datastax.driver.core.PreparedStatement;
import io.vertx.cassandra.CassandraClient;
import io.vertx.cassandra.CassandraClientOptions;
import io.vertx.cassandra.ResultSet;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(AppVerticle.class);

    private final static String CASSANDRA_HOST = "localhost";
    private final static int CASSANDRA_PORT = 9042;
    private final static int APP_PORT = 8080;

    private CassandraClient client;

    private PreparedStatement insertNewLinkForUser;

    @Override
    public void start(Future<Void> startFuture) {
        client = CassandraClient.createShared(vertx, new CassandraClientOptions().addContactPoint(CASSANDRA_HOST).setPort(CASSANDRA_PORT));
        Future<Void> future = Future.future();
        client.connect(future);
        future.compose(connected -> initKeyspaceIfNotExist())
                .compose(keySpacesInitialized -> prepareNecessaryQueries())
                .compose(all -> {
                    Future<String> deployed = Future.future();
                    vertx.deployVerticle(new FetchVerticle(), deployed);
                    return deployed;
                })
                .compose(deployed -> startHttpServer(), startFuture);
    }

    private Future<Void> initKeyspaceIfNotExist() {
        Future<Buffer> readFileFuture = Future.future();
        vertx.fileSystem().readFile("schema.cql", readFileFuture);
        return readFileFuture.compose(file -> {
            String[] statements = file.toString().split("\n");
            Future<ResultSet> result = Future.succeededFuture();
            for (String statement : statements) {
                result = result.compose(f -> {
                    Future<ResultSet> executionQueryFuture = Future.future();
                    client.execute(statement, executionQueryFuture);
                    return executionQueryFuture;
                });
            }
            return result;
        }).mapEmpty();
    }

    private Future<Void> prepareNecessaryQueries() {
        // TODO STEP 1
        return Future.succeededFuture();
    }

    @SuppressWarnings("UnusedReturnValue")
    private Future<HttpServer> startHttpServer() {
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        router.route().handler(ctx -> {
            ctx.response().putHeader("Access-Control-Allow-Origin", "*");
            ctx.next();
        });

        router.post("/user/:user_id/rss_link").handler(this::postRssLink);
        router.get("/user/:user_id/rss_channels").handler(this::getRssChannels);
        router.get("/articles/by_rss_link").handler(this::getArticles);
        router.get("/").handler(StaticHandler.create());

        Future<HttpServer> serverStarted = Future.future();
        server.requestHandler(router).listen(APP_PORT, serverStarted);
        return serverStarted;
    }

    private void getArticles(RoutingContext ctx) {
        // TODO STEP 3
    }

    private void getRssChannels(RoutingContext ctx) {
        // TODO STEP 2
    }

    private void postRssLink(RoutingContext ctx) {
        // TODO STEP 1
    }
}
