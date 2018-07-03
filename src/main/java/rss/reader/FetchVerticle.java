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

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.SimpleStatement;
import io.vertx.cassandra.CassandraClient;
import io.vertx.cassandra.ResultSet;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rss.reader.parsing.Article;
import rss.reader.parsing.RssChannel;

public class FetchVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(FetchVerticle.class);

    private final CassandraClient cassandraClient;
    private WebClient webClient;

    public FetchVerticle(CassandraClient cassandraClient) {
        this.cassandraClient = cassandraClient;
    }

    @Override
    public void start() throws Exception {
        webClient = WebClient.create(vertx);

        vertx.eventBus().localConsumer("fetch.rss.link", message -> {
            String rssLink = (String) message.body();
            log.info("fetching " + rssLink);
            webClient.getAbs(rssLink).send(response -> {
                if (response.succeeded()) {
                    String bodyAsString = response.result().bodyAsString("UTF-8");
                    try {
                        RssChannel rssChannel = new RssChannel(bodyAsString);
                        BatchStatement batchStatement = new BatchStatement();
                        String channelInfoInsertQuery = String.format(
                                "INSERT INTO channel_info_by_rss_link ( rss_link , last_fetch_time, description , site_link , title ) VALUES ('%s', '%d','%s', '%s','%s');",
                                rssLink, System.currentTimeMillis(), rssChannel.description, rssChannel.link, rssChannel.title
                        );
                        batchStatement.add(new SimpleStatement(channelInfoInsertQuery));
                        Future<PreparedStatement> future = Future.future();
                        cassandraClient.prepare("INSERT INTO articles_by_rss_link ( rss_link , pubdate , article_link , description , title ) VALUES ( ?, ?, ?, ?, ?);", future);
                        future.compose(preparedStatement -> {
                            for (Article article: rssChannel.articles) {
                                batchStatement.add(preparedStatement.bind(rssLink, article.pubDate, article.link, article.description, article.title));
                            }
                            Future<ResultSet> insertArticlesFuture = Future.future();
                            cassandraClient.execute(batchStatement, insertArticlesFuture);
                            return insertArticlesFuture;
                        }).compose(insertDone -> {
                            message.reply(FetchStatus.SUCCESS);
                            return Future.succeededFuture();
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        message.reply(FetchStatus.FAIL);
                    }
                } else {
                    message.reply(FetchStatus.FAIL);
                }
            });
        });
    }
}