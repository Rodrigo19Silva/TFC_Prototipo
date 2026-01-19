package pt.ulusofona.tfc;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class LLMInteractionEngine {

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
        json += "\"" + "model" + "\": " + "\"" + model + "\",";
        json += "\"" + "prompt" + "\" :" + "\"" + prompt + "\"";
        json += "}";
        return json;
    }

    String sendPrompt(String prompt) throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
        if(useHack) {
            return sendPrompt_Hack(prompt);
        }
        // pedido normal
        HttpClient client = HttpClient.newHttpClient();
        String json = buildJSON(model, prompt);
        return sendRequestToClientAndGetReply(client, url, apiKey, json);
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

        HttpClient insecureClient = HttpClient.newBuilder().sslContext(sc).build();
        // fim do hack
        // *************

        String json = buildJSON(model, prompt);

        return sendRequestToClientAndGetReply(insecureClient, url, apiKey, json);
    }

    String sendRequestToClientAndGetReply(HttpClient client, String url, String apiKey, String json) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        return resp.body();
    }
}