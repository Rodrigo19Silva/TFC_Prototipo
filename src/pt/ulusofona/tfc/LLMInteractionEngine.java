package pt.ulusofona.tfc;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

public class LLMInteractionEngine {
    private static final int CONNECT_TIMEOUT_SECONDS = 20;
    private static final int REQUEST_TIMEOUT_SECONDS = 180;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_INITIAL_BACKOFF_MS = 1200L;

    String url;
    String apiKey;
    String model;
    boolean useHack;

    LLMInteractionEngine(String url, String apiKey, String model, boolean useHack) {
        this.url = url;
        this.apiKey = apiKey;
        this.model = model;
        this.useHack = useHack;
    }
    LLMInteractionEngine(String url, String apiKey, String model) {
        this.url = url;
        this.apiKey = apiKey;
        this.model = model;
        this.useHack = false;
    }

    String buildJSON(String model, String prompt) {
        String json = "";
        json += "{";
        json += "\"" + "model" + "\": " + "\"" + JSONUtils.escapeJsonString(model) + "\",";
        json += "\"" + "prompt" + "\" :" + "\"" + JSONUtils.escapeJsonString(prompt) + "\"";
        json += "}";
        return json;
    }

    String sendPrompt(String prompt) throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
        return sendWithRetries(prompt, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_BACKOFF_MS);
    }

    String sendWithRetries(String prompt, int maxRetries, long initialBackoffMs)
            throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {

        int retries = Math.max(1, maxRetries);
        long baseBackoff = Math.max(200L, initialBackoffMs);

        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                if (useHack) {
                    return sendPrompt_Hack(prompt);
                }

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                        .build();

                String json = buildJSON(model, prompt);
                return sendRequestToClientAndGetReply(client, url, apiKey, json);
            } catch (LLMRequestException e) {
                if (!e.retryable || attempt == retries) {
                    throw e;
                }
                Thread.sleep(baseBackoff * attempt);
            } catch (HttpTimeoutException e) {
                if (attempt == retries) {
                    throw new IOException("Timeout ao comunicar com o servidor LLM.", e);
                }
                Thread.sleep(baseBackoff * attempt);
            } catch (IOException e) {
                if (!isRetryableNetworkError(e) || attempt == retries) {
                    throw e;
                }
                Thread.sleep(baseBackoff * attempt);
            }
        }

        throw new IOException("Falha inesperada no envio do prompt ao LLM.");
    }

    // aplicar a martelada para passar por cima do problema dos certificados
    String sendPrompt_Hack(String prompt) throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {

        // *************
        // hack por causa dos certificados
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, new TrustManager[]{ new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) {}
            public void checkServerTrusted(X509Certificate[] c, String a) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }}, new SecureRandom());

        HttpClient insecureClient = HttpClient.newBuilder()
                .sslContext(sc)
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        // fim do hack
        // *************

        String json = buildJSON(model, prompt);

        return sendRequestToClientAndGetReply(insecureClient, url, apiKey, json);
    }

    String sendRequestToClientAndGetReply(HttpClient client, String url, String apiKey, String json) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        int statusCode = resp.statusCode();
        String body = resp.body();

        if (statusCode >= 200 && statusCode < 300) {
            return body;
        }

        boolean retryable = isRetryableStatus(statusCode);
        String bodySnippet = body == null ? "" : body;
        if (bodySnippet.length() > 300) {
            bodySnippet = bodySnippet.substring(0, 300) + "...";
        }

        throw new LLMRequestException(
                "HTTP " + statusCode + " recebido do servidor LLM. Corpo: " + bodySnippet,
                statusCode,
                retryable
        );
    }

    private static boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || (statusCode >= 500 && statusCode <= 599);
    }

    private static boolean isRetryableNetworkError(IOException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }

        String lower = msg.toLowerCase();
        return lower.contains("timeout")
                || lower.contains("timed out")
                || lower.contains("connection reset")
                || lower.contains("connection aborted")
                || lower.contains("temporarily unavailable");
    }

    static class LLMRequestException extends IOException {
        final int statusCode;
        final boolean retryable;

        LLMRequestException(String message, int statusCode, boolean retryable) {
            super(message);
            this.statusCode = statusCode;
            this.retryable = retryable;
        }
    }
}
