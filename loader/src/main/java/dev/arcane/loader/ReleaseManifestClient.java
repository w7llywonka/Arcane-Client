package dev.arcane.loader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class ReleaseManifestClient {
    private static final int MAX_MANIFEST_BYTES = 32 * 1024;
    private final HttpClient client;
    private final URI manifestUri;

    ReleaseManifestClient(String manifestUrl) {
        manifestUri = checkedUri(manifestUrl);
        client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    ReleaseManifest latest() throws Exception {
        String separator = manifestUri.getRawQuery() == null ? "?" : "&";
        URI cacheBusted = URI.create(manifestUri + separator + "check=" + System.currentTimeMillis());
        HttpRequest request = HttpRequest.newBuilder(cacheBusted)
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .GET()
            .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        checkedUri(response.uri().toString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Release check failed with HTTP " + response.statusCode());
        }
        if (response.body().length == 0 || response.body().length > MAX_MANIFEST_BYTES) {
            throw new SecurityException("Release manifest size is invalid");
        }
        return ReleaseManifest.parse(new String(response.body(), StandardCharsets.UTF_8));
    }

    static URI checkedUri(String value) {
        URI uri = URI.create(value);
        boolean local = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!("https".equalsIgnoreCase(uri.getScheme()) || (local && "http".equalsIgnoreCase(uri.getScheme())))) {
            throw new SecurityException("Arcane update URLs must use HTTPS");
        }
        return uri;
    }
}
