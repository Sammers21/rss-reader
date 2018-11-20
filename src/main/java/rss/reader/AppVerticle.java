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
import com.datastax.driver.core.Row;
import io.vertx.cassandra.CassandraClient;
import io.vertx.cassandra.CassandraClientOptions;
import io.vertx.cassandra.ResultSet;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class AppVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(AppVerticle.class);

    private final static String CASSANDRA_HOST = "localhost";
    private final static int CASSANDRA_PORT = 9042;
    private final static int APP_PORT = 8080;

    private CassandraClient client;

    private AtomicReference<PreparedStatement> selectChannelInfo = new AtomicReference<>();
    private AtomicReference<PreparedStatement> selectRssLinksByLogin = new AtomicReference<>();
    private AtomicReference<PreparedStatement> insertNewLinkForUser = new AtomicReference<>();
    private AtomicReference<PreparedStatement> selectArticlesByRssLink = new AtomicReference<>();

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
        return CompositeFuture.all(
                prepareSelectChannelInfo(),
                prepareSelectRssLinksByLogin(),
                prepareInsertNewLinkForUser(),
                prepareSelectArticlesByRssLink()
        ).mapEmpty();
    }

    private Future<Void> prepareSelectChannelInfo() {
        return Util.prepareQueryAndSetReference(client,
                "SELECT description, title, site_link, rss_link FROM channel_info_by_rss_link WHERE rss_link = ? ;",
                selectChannelInfo
        );
    }

    private Future<Void> prepareSelectRssLinksByLogin() {
        return Util.prepareQueryAndSetReference(client,
                "SELECT rss_link FROM rss_by_user WHERE login = ? ;",
                selectRssLinksByLogin
        );
    }

    private Future<Void> prepareInsertNewLinkForUser() {
        return Util.prepareQueryAndSetReference(client,
                "INSERT INTO rss_by_user (login , rss_link ) VALUES ( ?, ?);",
                insertNewLinkForUser
        );
    }

    private Future<Void> prepareSelectArticlesByRssLink() {
        return Util.prepareQueryAndSetReference(client,
                "SELECT title, article_link, description, pubDate FROM articles_by_rss_link WHERE rss_link = ? ;",
                selectArticlesByRssLink
        );
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
        String link = ctx.request().getParam("link");
        if (link == null) {
            responseWithInvalidRequest(ctx);
        } else {
            client.executeWithFullFetch(selectArticlesByRssLink.get().bind(link), handler -> {
                if (handler.succeeded()) {
                    List<Row> rows = handler.result();

                    JsonObject responseJson = new JsonObject();
                    JsonArray articles = new JsonArray();

                    rows.forEach(eachRow -> articles.add(
                            new JsonObject()
                                    .put("title", eachRow.getString(0))
                                    .put("link", eachRow.getString(1))
                                    .put("description", eachRow.getString(2))
                                    .put("pub_date", eachRow.getTimestamp(3).getTime())
                    ));

                    responseJson.put("articles", articles);
                    ctx.response().end(responseJson.toString());
                } else {
                    log.error("failed to get articles for " + link, handler.cause());
                    ctx.response().setStatusCode(500).end("Unable to retrieve the info from C*");
                }
            });
        }
    }

    private void getRssChannels(RoutingContext ctx) {
        String userId = ctx.request().getParam("user_id");
        if (userId == null) {
            responseWithInvalidRequest(ctx);
        } else {
            Future<List<Row>> future = Future.future();
            client.executeWithFullFetch(selectRssLinksByLogin.get().bind(userId), future);
            future.compose(rows -> {
                List<String> links = rows.stream()
                        .map(row -> row.getString(0))
                        .collect(Collectors.toList());

                return CompositeFuture.all(
                        links.stream().map(selectChannelInfo.get()::bind).map(statement -> {
                            Future<List<Row>> channelInfoRow = Future.future();
                            client.executeWithFullFetch(statement, channelInfoRow);
                            return channelInfoRow;
                        }).collect(Collectors.toList())
                );
            }).setHandler(h -> {
                if (h.succeeded()) {
                    CompositeFuture result = h.result();
                    List<List<Row>> results = result.list();
                    List<Row> list = results.stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    JsonObject responseJson = new JsonObject();
                    JsonArray channels = new JsonArray();

                    list.forEach(eachRow -> channels.add(
                            new JsonObject()
                                    .put("description", eachRow.getString(0))
                                    .put("title", eachRow.getString(1))
                                    .put("link", eachRow.getString(2))
                                    .put("rss_link", eachRow.getString(3))
                    ));

                    responseJson.put("channels", channels);
                    ctx.response().end(responseJson.toString());
                } else {
                    log.error("failed to get rss channels", h.cause());
                    ctx.response().setStatusCode(500).end("Unable to retrieve the info from C*");
                }
            });
        }
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
                Future<ResultSet> future = Future.future();
                BoundStatement query = insertNewLinkForUser.get().bind(userId, link);
                client.execute(query, future);
                future.setHandler(result -> {
                    if (result.succeeded()) {
                        ctx.response().end(new JsonObject().put("message", "The feed just added").toString());
                    } else {
                        ctx.response().setStatusCode(400).end(result.cause().getMessage());
                    }
                });
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
