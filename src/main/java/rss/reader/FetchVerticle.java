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
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.reactivex.cassandra.CassandraClient;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rss.reader.parsing.Article;
import rss.reader.parsing.RssChannel;

import java.util.Date;

import static rss.reader.AppVerticle.NOTHING;

@SuppressWarnings("unchecked")
public class FetchVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(FetchVerticle.class);

    private CassandraClient cassandraClient;
    private WebClient webClient;

    // TODO initialize on step 1
    private PreparedStatement insertChannelInfo;
    private PreparedStatement insertArticleInfo;

    @Override
    public void start(Future<Void> startFuture) {
        webClient = WebClient.create(vertx);
        cassandraClient = CassandraClient.createShared(vertx);
        startFetchEventBusConsumer();
        prepareNecessaryQueries()
                .toCompletable()
                .subscribe(startFuture::complete, startFuture::fail);
    }

    private void startFetchEventBusConsumer() {
        vertx.eventBus().localConsumer("fetch.rss.link", message -> {
            String rssLink = (String) message.body();
            log.info("fetching " + rssLink);
            webClient.getAbs(rssLink).rxSend()
                    .subscribe(response -> {
                        String bodyAsString = response.bodyAsString("UTF-8");
                        try {
                            RssChannel rssChannel = new RssChannel(bodyAsString);
                            BatchStatement batchStatement = new BatchStatement();
                            BoundStatement channelInfoInsertQuery = insertChannelInfo.bind(
                                    rssLink, new Date(System.currentTimeMillis()), rssChannel.description, rssChannel.link, rssChannel.title
                            );
                            batchStatement.add(channelInfoInsertQuery);
                            for (Article article : rssChannel.articles) {
                                batchStatement.add(insertArticleInfo.bind(rssLink, article.pubDate, article.link, article.description, article.title));
                            }
                            cassandraClient.rxExecute(batchStatement).subscribe(
                                    done -> log.info("Storage have just been updated with entries from: " + rssLink),
                                    error -> log.error("Unable to update storage with entities fetched from: " + rssLink, error)
                            );
                        } catch (Exception e) {
                            log.error("Unable to handle: " + rssLink);
                        }
                    }, e -> {
                        log.error("Unable to fetch: " + rssLink, e);
                    });
        });
    }

    private Single prepareNecessaryQueries() {
        // TODO STEP 1
        return Single.just(NOTHING);
    }
}