package com.pulseai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.Test;

class ArticleImageValidatorTest {

    private static final String ACCEPT = "image/avif,image/webp,image/apng,image/svg+xml,image/*;q=0.9,*/*;q=0.5";
    private static final URI IMAGE = URI.create("https://93.184.216.34/story.jpg");
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01, 0x02};

    private final ProviderHttpClient client = mock(ProviderHttpClient.class);
    private final ArticleImageValidator validator = new ArticleImageValidator(client, new ArticleNormalizer());

    @Test
    void acceptsAnImageResponseAndCachesValidationByUrl() throws Exception {
        when(client.fetchOnceWithStatus(IMAGE, ACCEPT))
                .thenReturn(new ProviderHttpResponse(200, JPEG, null, "image/jpeg; charset=binary"));

        assertThat(validator.validate(IMAGE.toString())).isEqualTo(ArticleImageValidator.ValidationResult.VALID);
        assertThat(validator.validate(IMAGE.toString())).isEqualTo(ArticleImageValidator.ValidationResult.VALID);
        assertThat(validator.hasCachedResult(IMAGE.toString())).isTrue();
        verify(client, times(1)).fetchOnceWithStatus(IMAGE, ACCEPT);
    }

    @Test
    void rejectsHtmlPagesEvenWhenTheServerLabelsThemAsImages() throws Exception {
        when(client.fetchOnceWithStatus(IMAGE, ACCEPT))
                .thenReturn(new ProviderHttpResponse(200, "<html>expired link</html>".getBytes(), null, "image/jpeg"));

        assertThat(validator.validate(IMAGE.toString())).isEqualTo(ArticleImageValidator.ValidationResult.INVALID);
    }

    @Test
    void rejectsHtmlContentTypesAndBrokenPublisherResponses() throws Exception {
        when(client.fetchOnceWithStatus(IMAGE, ACCEPT))
                .thenReturn(new ProviderHttpResponse(404, "not found".getBytes(), null, "text/html"));

        assertThat(validator.validate(IMAGE.toString())).isEqualTo(ArticleImageValidator.ValidationResult.INVALID);
    }

    @Test
    void reportsTransientServerErrorsAsUnavailableInsteadOfErasingAStoredUrl() throws Exception {
        when(client.fetchOnceWithStatus(IMAGE, ACCEPT))
                .thenReturn(new ProviderHttpResponse(503, new byte[0], null, "text/html"));

        assertThat(validator.validate(IMAGE.toString())).isEqualTo(ArticleImageValidator.ValidationResult.UNAVAILABLE);
    }

    @Test
    void refusesPrivateAddressRedirects() throws Exception {
        when(client.fetchOnceWithStatus(IMAGE, ACCEPT))
                .thenReturn(new ProviderHttpResponse(302, new byte[0], URI.create("http://127.0.0.1/private")));

        assertThat(validator.validate(IMAGE.toString())).isEqualTo(ArticleImageValidator.ValidationResult.INVALID);
        verify(client, times(1)).fetchOnceWithStatus(IMAGE, ACCEPT);
    }

    @Test
    void rejectsNonHttpAndCredentialBearingUrlsWithoutNetworkRequests() {
        assertThat(validator.validate("javascript:alert(1)")).isEqualTo(ArticleImageValidator.ValidationResult.INVALID);
        assertThat(validator.validate("https://user:pass@93.184.216.34/photo.jpg"))
                .isEqualTo(ArticleImageValidator.ValidationResult.INVALID);
        org.mockito.Mockito.verifyNoInteractions(client);
    }
}
