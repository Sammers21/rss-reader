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
package rss.reader.parsing;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public class Article {

    public final Date pubDate;
    public final String title;
    public final String description;
    public final String link;

    public Article(LocalDate pubDate, String title, String description, String link) {
        this.pubDate = Date.from(pubDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        this.title = title;
        this.description = description;
        this.link = link;
    }
}
