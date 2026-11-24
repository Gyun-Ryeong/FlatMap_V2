package com.flatmap.navigation.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class JdkIngestionHttp implements IngestionHttp {

    private final HttpClient client;
    private final Duration readTimeout;

    public JdkIngestionHttp(@Value("${ingestion.connect-timeout-ms:10000}") long connectTimeoutMs,
                            @Value("${ingestion.read-timeout-ms:30000}") long readTimeoutMs) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
    }

    @Override
    public String get(String url) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(readTimeout)
                    .header("Accept", "application/json, application/xml, */*")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            // 인코딩을 서버 헤더에 맡기지 않고 UTF-8 로 고정한다.
            return new String(response.body(), StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        } catch (IllegalArgumentException e) {
            throw new IOException("bad url");
        }
        // 그 외 IOException(연결 거부·타임아웃)은 URL 을 담지 않으므로 그대로 올려 보낸다.
    }
}
