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
import io.vertx.reactivex.cassandra.CassandraClient;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rss.reader.parsing.RssChannel;

public class FetchVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(FetchVerticle.class);

    private CassandraClient cassandraClient;
    private WebClient webClient;

    private PreparedStatement insertChannelInfo;
    private PreparedStatement insertArticleInfo;

    @Override
    public void start(Future<Void> startFuture) {
        webClient = WebClient.create(vertx);
        cassandraClient = CassandraClient.createShared(vertx);
        startFetchEventBusConsumer();
        prepareNecessaryQueries().setHandler(startFuture);
    }

    private void startFetchEventBusConsumer() {
        vertx.eventBus().localConsumer("fetch.rss.link", message -> {
            String rssLink = (String) message.body();
            log.info("fetching " + rssLink);
            webClient.getAbs(rssLink).send(response -> {
                if (response.succeeded()) {
                    String bodyAsString = response.result().bodyAsString("UTF-8");
                    try {
                        RssChannel rssChannel = new RssChannel(bodyAsString);

                        // TODO STEP 1
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


    private Future<Void> prepareNecessaryQueries() {
        // TODO STEP 1
        return Future.succeededFuture();
    }
}