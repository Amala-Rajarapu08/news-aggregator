package com.pulseai.ingestion;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
@Component
public class HttpRssFeedFetcher implements RssFeedFetcher {
    private final ProviderHttpClient client;
    public HttpRssFeedFetcher(ProviderHttpClient client) { this.client=client; }
    public String fetch(String url) throws IOException,InterruptedException {
        RssFeedResponse response = fetchWithStatus(url);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("RSS provider returned HTTP " + response.statusCode() + ".");
        }
        return response.body();
    }

    @Override
    public RssFeedResponse fetchWithStatus(String url) throws IOException,InterruptedException {
        try {
            var response = client.fetchWithStatus(URI.create(url),"application/rss+xml, application/atom+xml, application/xml, text/xml",null);
            return new RssFeedResponse(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        }
        catch(IllegalArgumentException e) { throw new IOException("Invalid RSS URL."); }
    }
}
