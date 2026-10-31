package ru.reschikov;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class NetworkProvider {

    private static final String KEY = "408cdc9b-15bf-40f5-b148-9c5c610c383b";
    private static final String BASE_URL = "https://api.weather.yandex.ru/v2/forecast";
    private final HttpClient httpClient;

    public NetworkProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public CompletableFuture<HttpResponse<String>> getMeteoData(Main.Request request){
        return httpClient.sendAsync(createGetRequest(request), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest createGetRequest(Main.Request request){
        return HttpRequest.newBuilder()
                .uri(URI.create(createUrl(request.lat(), request.lon(), request.limit())))
                .header("X-Yandex-Weather-Key", KEY)
                .GET()
                .build();

    }

    private String createUrl(double lat, double lon, int limit) {
        return String.format("%s?lat=%s&lon=%s&limit=%d", BASE_URL, lat, lon, limit);
    }
}
