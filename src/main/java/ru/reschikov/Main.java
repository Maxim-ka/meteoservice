package ru.reschikov;

import java.io.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

//TIP Для <b>запуска</b> кода нажмите <shortcut actionId="Run"/> или
// щелкните значок <icon src="AllIcons.Actions.Execute"/> в боковой области.
public class Main {

    private static final String PATTERN_OUT = "^[qQйЙ]$";
    private static final String PATTERN_NUMBER = "^[+-]?\\d+(\\.\\d+)?$";
    private static final int DELAY = 500;
    private static final int LINE_DOT = 10;

    private static String input = "";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Service service = new Service(new NetworkProvider(HttpClient.newHttpClient()));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        do {
            try {
                show(writer, "Выход - Q", !input.isEmpty(), false);
                insertNewLine(writer);
                Request request = input(scanner, writer);
                if (!input.matches(PATTERN_OUT)) {
                    show(writer,"Ожидание", false, false);
                    insertNewLine(writer);
                    processMeteoData(writer, service, request);
                }
            } catch (IOException e) {
                try {
                    show(writer, e.getMessage(), true, true);
                } catch (IOException ignored) {}
            }
        } while (!input.matches(PATTERN_OUT));
        try {
            show(writer, "Выход", false, true);
        } catch (IOException ignored) {
        } finally {
            scanner.close();
            try {
                writer.close();
            } catch (IOException ignored) { }
        }
    }

    private static void processMeteoData(BufferedWriter writer, Service service, Request request) throws IOException{
        AtomicBoolean isWait = new AtomicBoolean(true);
        try {
            service.getMeteoData(request, (string, isEnd) -> {
                synchronized (writer){
                    try {
                        show(writer, string, true, false);
                        if (isEnd) {
                            isWait.set(false);
                        }
                    } catch (IOException e) {
                        isWait.set(false);
                        try {
                            show(writer, "Ошибка записи данных: " + e.getMessage(), true, true);
                        } catch (IOException ignored) {}
                    }finally {
                        writer.notifyAll();
                    }
                }
            });
            int count = 0;
            while (isWait.get()){
                synchronized (writer) {
                    writer.wait(DELAY);
                    if (!isWait.get()) break;
                    if (count >= LINE_DOT) {
                        count = 0;
                        writer.newLine();
                    }
                    show(writer, ". ", false, false);
                    count++;
                }
            }
            insertNewLine(writer);
            show(writer,"Завершение вывода результата", false, true);
        } catch (IOException e) {
            throw new IOException("Ошибка записи:\n " + e.getMessage());
        } catch (InterruptedException e) {
            throw new IOException("Ошибка ожидания:\n " + e.getMessage());
        }
    }


    private static Request input(Scanner scanner, BufferedWriter writer){
        int limit = 0;
        double lat = Double.NaN;
        double lon = Double.NaN;
        do{
            try {
                if (Double.isNaN(lat)) {
                   show(writer, "Введите координаты широты: ", false, false);
                } else if (Double.isNaN(lon)){
                    show(writer,"Введите координаты долготы: ", false, false);
                } else {
                    show(writer,"Введите лимит прогноза, дней: ", false, false);
                }
                input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    show(writer, "ошибка ввода: пустая строка", false, true);
                    continue;
                }
                if (!input.matches(PATTERN_OUT)) {
                    if (!input.matches(PATTERN_NUMBER)) {
                        show(writer, "ошибка ввода: " + input, false, true);
                        continue;
                    }
                    if (Double.isNaN(lat) || Double.isNaN(lon)) {
                        double tmp = Double.parseDouble(input);
                        if (Double.isNaN(lat)) {
                            if (tmp < -90 || tmp > 90) {
                                show(writer, "Широта должна быть от -90 до 90", false, true);
                            } else {
                                lat = tmp;
                            }
                        } else {
                            if (tmp < -180 || tmp > 180) {
                                show(writer, "Долгота должна быть от -180 до 180", false, true);
                            } else {
                                lon = tmp;
                            }
                        }
                    } else {
                        if (input.contains(".")) {
                            show(writer, "Кол-во дней для прогноза - целое число", false, true);
                            continue;
                        }
                        limit = Integer.parseInt(input);
                        if (limit < 1) {
                            show(writer, "Кол-во дней для прогноза не может быть < 1", false, true);
                            continue;
                        }
                        if (limit > 7) {
                            show(writer, "Кол-во дней для прогноза не может быть > 7", false, true);
                        }
                    }
                }
            } catch (IOException ignore) { }
        } while (!input.matches(PATTERN_OUT) && (Double.isNaN(lat) || Double.isNaN(lon) || limit < 1 || limit > 7));
        return new Request(lat, lon, limit);
    }

    private static void show(BufferedWriter writer, String string, boolean hasBeforeLine, boolean hasAfterLine) throws IOException {
        if (hasBeforeLine) writer.newLine();
        writer.write(string);
        if (hasAfterLine) writer.newLine();
        writer.flush();
    }

    private static void insertNewLine(BufferedWriter writer) throws IOException {
        writer.newLine();
        writer.flush();
    }

    public record Request(double lat, double lon, int limit){}


}