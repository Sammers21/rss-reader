CREATE KEYSPACE IF NOT EXISTS rss_reader WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1 } ;
CREATE TABLE IF NOT EXISTS rss_reader.rss_by_user (login text , rss_link text, PRIMARY KEY (login, rss_link));
CREATE TABLE IF NOT EXISTS rss_reader.articles_by_rss_link(rss_link text, pubDate timestamp, title text, article_link text, description text, PRIMARY KEY ( rss_link , pubDate , article_link));
CREATE TABLE IF NOT EXISTS rss_reader.channel_info_by_rss_link(rss_link text, last_fetch_time timestamp,title text, site_link text, description text, PRIMARY KEY(rss_link));