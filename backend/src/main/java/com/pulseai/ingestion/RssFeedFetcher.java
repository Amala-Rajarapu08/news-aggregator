package com.pulseai.ingestion;

import java.io.IOException;

public interface RssFeedFetcher {
    String fetch(String feedUrl) throws IOException, InterruptedException;

    default RssFeedResponse fetchWithStatus(String feedUrl) throws IOException, InterruptedException {
        return new RssFeedResponse(200, fetch(feedUrl));
    }
}
