package dev.arcane.loader;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@FunctionalInterface
interface ArtifactDownloader {
    void download(URI source, Path destination, long maximumBytes) throws Exception;

    static ArtifactDownloader http() {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        return (source, destination, maximumBytes) -> {
            HttpRequest request = HttpRequest.newBuilder(source)
                .timeout(Duration.ofMinutes(2))
                .header("Cache-Control", "no-cache")
                .GET()
                .build();
            HttpResponse<InputStream> response = client.send(
                request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IllegalStateException("Loader download failed with HTTP " + response.statusCode());
            }
            ReleaseManifestClient.checkedUri(response.uri().toString());
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > maximumBytes) {
                response.body().close();
                throw new SecurityException("Loader download exceeds its maximum size");
            }
            try (InputStream input = response.body(); var output = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[16 * 1024];
                long total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > maximumBytes) {
                        throw new SecurityException("Loader download exceeds its maximum size");
                    }
                    output.write(buffer, 0, read);
                }
            }
        };
    }
}
