package ru.reschikov;

import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Service {

    private final NetworkProvider networkProvider;
    private final Parser parser = new Parser();

    public Service(NetworkProvider networkProvider) {
        this.networkProvider = networkProvider;
    }

    public void getMeteoData(Main.Request request, DataCallback callback) {
        networkProvider.getMeteoData(request)
            .whenComplete((response, error) -> {
                if (error != null) {
                    callback.take(error.getMessage(), true);
                }
                if (response != null) {
                    if (response.statusCode() == 200) {
                        handle(response, callback);
                    } else {
                        callback.take(response.body(), true);
                    }
                }
            });

    }

    private void handle(HttpResponse<String> response, DataCallback callback) {
        String string = response.body();
        if (string.isEmpty()) {
            callback.take("Пустой ответ", true);
            return;
        }
        String temps = parser.getTemps(string);
        callback.take(string, false);
        callback.take(temps, true);
    }

    private static class Parser {

        private static final Pattern PATTERN_FACT_TEMP = Pattern.compile("\"fact\"\\s*:\\s*\\{.*?\"temp\"\\s*:\\s*(?<temp>-?\\d+(?:\\.\\d+)?)");
        private static final Pattern PATTERN_DAY_TEMP_AVG = Pattern.compile("\"day\"\\s*:\\s*\\{.*?\"temp_avg\"\\s*:\\s*(?<tempAvg>-?\\d+(?:\\.\\d+)?)");


        private String getTemps(String json) {
            CompletableFuture<String> futureFactTemp = CompletableFuture.supplyAsync(() -> takeFactTemp(json));
            CompletableFuture<Forecast> futureTempAvg = CompletableFuture.supplyAsync(() -> getForecastTempAvg(takeListTempAvg(json)));
            return futureFactTemp.thenCombine(futureTempAvg, (fact, forecast) ->
                String.format("Средняя температура по прогнозу: %s %s\nТекущая температура: %s",
                    forecast.tempAvg, forecast.days != 0 ? String.format("за %s дней", forecast.days) : "", fact)
            ).join();
        }

        private String takeFactTemp(String json) {
            Matcher matcher = PATTERN_FACT_TEMP.matcher(json);
            if (matcher.find()){
                return matcher.group("temp");
            }
            return "Значения temp не найдены";
        }

        private Forecast getForecastTempAvg(List<Float> list) {
            if (list.isEmpty()) return new Forecast("Нет данных", 0);
            return new Forecast(
                    String.format("%.2f",(list.stream().mapToDouble(Float::doubleValue).sum() / list.size())),
                    list.size());
        }

        private List<Float> takeListTempAvg(String json) {
            List<Float> list = new ArrayList<>();
            Matcher matcher = PATTERN_DAY_TEMP_AVG.matcher(json);
            while (matcher.find()) {
                try {
                    list.add(Float.parseFloat(matcher.group("tempAvg")));
                } catch (NumberFormatException e) {
                    System.out.println(e.getMessage());
                }
            }
            return list;
        }
    }

    private record Forecast(String tempAvg, int days){}
}
