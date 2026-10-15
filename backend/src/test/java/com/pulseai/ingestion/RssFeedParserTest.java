package com.pulseai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class RssFeedParserTest {

    private final RssFeedParser parser = new RssFeedParser();

    @Test
    void parsesRssArticleMetadataAndFeedContext() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                  <channel>
                    <title>Example Journal</title>
                    <link>https://example.com/</link>
                    <language>en-US</language>
                    <item>
                      <guid>story-17</guid>
                      <title>Research advances</title>
                      <description><![CDATA[<p>New research details.</p>]]></description>
                      <link>https://example.com/science/research</link>
                      <pubDate>Mon, 05 Oct 2026 08:30:00 GMT</pubDate>
                      <author>Reporter</author>
                      <category>Science</category>
                      <media:thumbnail url="https://example.com/images/story.jpg"/>
                    </item>
                    <item><title>Incomplete item</title></item>
                  </channel>
                </rss>
                """;

        List<ProviderArticle> result = parser.parse(xml);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).externalId()).isEqualTo("story-17");
        assertThat(result.get(0).sourceName()).isEqualTo("Example Journal");
        assertThat(result.get(0).sourceHomeUrl()).isEqualTo("https://example.com/");
        assertThat(result.get(0).imageUrl()).isEqualTo("https://example.com/images/story.jpg");
        assertThat(result.get(0).category()).isEqualTo("Science");
        assertThat(result.get(1).publishedAt()).isNull();
    }

    @Test
    void rejectsXmlContainingDoctypeDeclarations() {
        String xml = """
                <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///windows/win.ini">]>
                <rss><channel><title>&xxe;</title></channel></rss>
                """;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(org.xml.sax.SAXException.class);
    }

    @org.junit.jupiter.api.Test
    void extractsPublisherImageFromEncodedContentWhenDescriptionHasNoImage() throws Exception {
        var parser = new RssFeedParser();
        var entries = parser.parse("""
            <rss xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel>
              <title>Publisher</title><link>https://publisher.example</link>
              <item><title>Story</title><link>https://publisher.example/story</link>
              <description>Short publisher excerpt.</description>
              <content:encoded><![CDATA[<p>Publisher content</p><img src="https://publisher.example/photo.jpg?a=1&amp;b=2">]]></content:encoded>
              <pubDate>Mon, 12 Oct 2026 08:00:00 GMT</pubDate></item>
            </channel></rss>
            """);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).imageUrl()).isEqualTo("https://publisher.example/photo.jpg?a=1&b=2");
    }

    @Test
    void leavesImageMissingWhenPublisherFeedDoesNotProvideOne() throws Exception {
        var entries = parser.parse("""
            <rss><channel>
              <title>Publisher</title><link>https://publisher.example</link>
              <item><title>Text-only story</title><link>https://publisher.example/story</link>
              <description>Publisher supplied excerpt with no image.</description>
              <pubDate>Mon, 12 Oct 2026 08:00:00 GMT</pubDate></item>
            </channel></rss>
            """);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).imageUrl()).isNull();
        assertThat(new ArticleNormalizer().normalize(entries.get(0)).imageUrl()).isNull();
    }

    @Test
    void readsNestedMediaRssImagesAndSkipsVideoMedia() throws Exception {
        var entries = parser.parse("""
            <rss xmlns:media="http://search.yahoo.com/mrss/"><channel>
              <title>Publisher</title><link>https://publisher.example/</link>
              <item><title>Nested media story</title><link>https://publisher.example/news/story</link>
                <pubDate>Mon, 12 Oct 2026 08:00:00 GMT</pubDate>
                <media:group>
                  <media:content url="https://publisher.example/video.mp4" medium="video" type="video/mp4"/>
                  <media:thumbnail url="//cdn.publisher.example/photo.webp"/>
                </media:group>
              </item>
              <item><title>Video only story</title><link>https://publisher.example/news/video</link>
                <pubDate>Mon, 12 Oct 2026 08:00:00 GMT</pubDate>
                <media:content url="https://publisher.example/video.mp4" medium="video" type="video/mp4"/>
              </item>
            </channel></rss>
            """);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).imageUrl()).isEqualTo("https://cdn.publisher.example/photo.webp");
        assertThat(entries.get(1).imageUrl()).isNull();
    }

    @Test
    void readsImageEnclosuresAndResolvesPublisherRelativeImageUrls() throws Exception {
        var entries = parser.parse("""
            <rss><channel><title>Publisher</title><link>https://publisher.example/</link>
              <item><title>Image enclosure</title><link>https://publisher.example/a/story</link>
                <enclosure url="https://cdn.publisher.example/enclosure" type="image/jpeg"/>
                <pubDate>Mon, 12 Oct 2026 08:00:00 GMT</pubDate>
              </item>
              <item><title>Relative image</title><link>https://publisher.example/a/story</link>
                <description><![CDATA[<img src="../images/story.jpg">]]></description>
                <pubDate>Mon, 12 Oct 2026 08:00:00 GMT</pubDate>
              </item>
            </channel></rss>
            """);

        assertThat(entries).extracting(ProviderArticle::imageUrl)
                .containsExactly("https://cdn.publisher.example/enclosure", "https://publisher.example/images/story.jpg");
    }

    @Test
    void rejectsUnsafePublisherImageSchemes() throws Exception {
        var entries = parser.parse("""
            <rss><channel><title>Publisher</title><link>https://publisher.example/</link>
              <item><title>Unsafe image</title><link>https://publisher.example/a/story</link>
                <enclosure url="file:///private/photo.jpg" type="image/jpeg"/>
                <pubDate>Mon, 12 Oct 2026 08:00:00 GMT</pubDate>
              </item>
            </channel></rss>
            """);

        assertThat(entries).singleElement().satisfies(article -> assertThat(article.imageUrl()).isNull());
    }
}
