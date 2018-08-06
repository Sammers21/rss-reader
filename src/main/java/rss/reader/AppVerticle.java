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

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.cassandra.CassandraClientOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.cassandra.CassandraClient;
import io.vertx.reactivex.cassandra.ResultSet;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unchecked")
public class AppVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(AppVerticle.class);

    private final static String CASSANDRA_HOST = "localhost";
    private final static int CASSANDRA_PORT = 9042;
    private final static int APP_PORT = 8080;
    private final static Object NOTHING = new Object();

    private CassandraClient client;

    private PreparedStatement insertNewLinkForUser;

    @Override
    public void start(Future<Void> startFuture) {
        CassandraClientOptions options = new CassandraClientOptions()
                .addContactPoint(CASSANDRA_HOST)
                .setPort(CASSANDRA_PORT);

        client = CassandraClient.createShared(vertx, options);
        client.rxConnect().toSingleDefault(NOTHING)
                .flatMap(connected -> {
                    log.info("Connected to Cassandra");
                    return initKeyspaceIfNotExist();
                })
                .flatMap(initialized -> {
                    log.info("Keyspace initialized");
                    return prepareNecessaryQueries();
                })
                .flatMap(prepared -> {
                    log.info("Necessary queries are prepared");
                    return vertx.rxDeployVerticle(FetchVerticle.class.getName());
                })
                .flatMap(deployed -> {
                    log.info("Fetch verticle deployed");
                    return startHttpServer();
                })
                .toCompletable()
                .subscribe(startFuture::complete, startFuture::fail);
    }

    private Single initKeyspaceIfNotExist() {
        Single<Buffer> file = vertx.fileSystem().rxReadFile("schema.cql");
        return file.flatMap(content -> {
            String[] statements = content.toString().split("\n");
            Single single = Single.just(NOTHING);
            for (String statement : statements) {
                single = single.flatMap(prev -> client.rxExecute(statement));
            }
            return single;
        });
    }

    private Single prepareNecessaryQueries() {
        Single<PreparedStatement> rxPrepare = client.rxPrepare("INSERT INTO rss_by_user (login , rss_link ) VALUES ( ?, ?);");
        rxPrepare.subscribe(preparedStatement -> insertNewLinkForUser = preparedStatement);
        return rxPrepare;
    }

    private Single startHttpServer() {
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

        return server.requestHandler(router).rxListen(APP_PORT);
    }

    private void getArticles(RoutingContext ctx) {
        // TODO STEP 3
    }

    private void getRssChannels(RoutingContext ctx) {
        // TODO STEP 2
    }

    private void postRssLink(RoutingContext ctx) {
        ctx.request().bodyHandler(body -> {
            JsonObject bodyAsJson = body.toJsonObject();
            String link = bodyAsJson.getString("link");
            String userId = ctx.request().getParam("user_id");
            if (link == null || userId == null) {
                responseWithInvalidRequest(ctx);
            } else {
                vertx.eventBus().send("fetch.rss.link", link);
                BoundStatement query = insertNewLinkForUser.bind(userId, link);
                client.rxExecute(query).subscribe(
                        result -> {
                            ctx.response().end(new JsonObject().put("message", "The feed just added").toString());
                        }, error -> {
                            ctx.response().setStatusCode(400).end(error.getMessage());
                        }
                );
            }
        });
    }

    private void responseWithInvalidRequest(RoutingContext ctx) {
        ctx.response()
                .setStatusCode(400)
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(invalidRequest().toString());
    }

    private JsonObject invalidRequest() {
        return new JsonObject().put("message", "Invalid request");
    }
}
