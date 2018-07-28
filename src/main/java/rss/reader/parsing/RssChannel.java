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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class RssChannel {

    public String title;
    public String link;
    public String description;

    public List<Article> articles;

    private static final DateTimeFormatter PUB_DATE_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME;

    public RssChannel(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(xml));
        Document doc = builder.parse(is);
        doc.getDocumentElement().normalize();
        NodeList childNodes = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (item.getNodeType() == Node.ELEMENT_NODE) {
                Element eItem = (Element) item;
                this.title = eItem.getElementsByTagName("title").item(0).getTextContent();
                this.link = eItem.getElementsByTagName("link").item(0).getTextContent();
                this.description = eItem.getElementsByTagName("description").item(0).getTextContent();
                NodeList channelChildren = eItem.getChildNodes();
                this.articles = new ArrayList<>();
                for (int y = 0; y < channelChildren.getLength(); y++) {
                    Node childNode = channelChildren.item(y);
                    if (childNode.getNodeType() == Node.ELEMENT_NODE && childNode.getNodeName().equals("item")) {
                        Element itemNode = (Element) childNode;
                        String link = itemNode.getElementsByTagName("link").item(0).getTextContent();
                        String description = itemNode.getElementsByTagName("description").item(0).getTextContent();
                        String title = itemNode.getElementsByTagName("title").item(0).getTextContent();
                        LocalDate pubDate = LocalDate.parse(itemNode.getElementsByTagName("pubDate").item(0).getTextContent(), PUB_DATE_FORMAT);
                        articles.add(new Article(pubDate, title, description, link));
                    }
                }
            }
        }
    }
}
