package org.example;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser {

    private static final String LOG_FILE_NAME = "3monthslog.txt";
    private static final String PARSED_LOG_FILE_NAME = "3monthslog.csv";
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("a h:mm", Locale.ENGLISH);
    private static final Pattern PATTERN_CLOUD_BOT = Pattern.compile("Cloud bot");
    private static final Pattern PATTERN_FOR_DAYS = Pattern.compile("APP  (\\d{1,2}:\\d{2}) ([AP]M)");
    private static final Pattern PATTERN_FOR_FINAL_RESULT = Pattern.compile("(\\d{2}:\\d{2}) (.+)");

    private static final Pattern[] OPERATIONS = {
            Pattern.compile("(команда создана)$"),
            Pattern.compile("(команда создана в графане)$"),
            Pattern.compile("(зарегистрировался)$"),
            Pattern.compile("(подписка для команды создана)$"),
            Pattern.compile("(запустил test)"),
            Pattern.compile("(перешел в status TEST_STOPPING)"),
            Pattern.compile("(перешел в status CANCELED)"),
            Pattern.compile("(стартовал grafana)"),
            Pattern.compile("(перешел в status FINISHED)$"),
            Pattern.compile("(перешел в status FAILED)$"),
            Pattern.compile("(ошибка регистрации)"),
            Pattern.compile("(обновил подписку)")
    };

    public static void main(String[] args) {
        StringBuilder tempResultWithoutCloudBot = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(LOG_FILE_NAME))) {
            processLogFile(reader, tempResultWithoutCloudBot);
        } catch (IOException e) {
            System.out.println("Ошибка чтения файла: " + LOG_FILE_NAME);
        }

        StringBuilder result = new StringBuilder("day,time,operation\n");
        parseOperations(tempResultWithoutCloudBot, result);

        try (FileWriter fileWriter = new FileWriter(PARSED_LOG_FILE_NAME)) {
            fileWriter.write(result.toString());
        } catch (IOException e) {
            System.out.println("Ошибка записи файла");
        }
    }

    private static void processLogFile(BufferedReader reader, StringBuilder tempResultWithoutCloudBot) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            Matcher matcherCloudBot = PATTERN_CLOUD_BOT.matcher(line);
            if (matcherCloudBot.find()) {
                processCloudBotEntry(reader, tempResultWithoutCloudBot);
            }
        }
    }

    private static void processCloudBotEntry(BufferedReader reader, StringBuilder tempResultWithoutCloudBot) throws IOException {
        String line = reader.readLine();
        Matcher matcherDays = PATTERN_FOR_DAYS.matcher(line);
        matcherDays.find();
        String timeOfFirstOperation = matcherDays.group(1);
        String amPm = matcherDays.group(2);
        String timeToConvert = amPm + " " + timeOfFirstOperation;
        LocalTime time = LocalTime.parse(timeToConvert, INPUT_FORMATTER);
        tempResultWithoutCloudBot.append(time).append(" ");
        line = reader.readLine();
        tempResultWithoutCloudBot.append(line).append("\n");

        while (!(line = reader.readLine()).equals("")) {
            timeToConvert = amPm + " " + line;
            time = LocalTime.parse(timeToConvert, INPUT_FORMATTER);
            tempResultWithoutCloudBot.append(time).append(" ");
            line = reader.readLine();
            tempResultWithoutCloudBot.append(line).append("\n");
        }
    }

    private static void parseOperations(StringBuilder tempResultWithoutCloudBot, StringBuilder result) {
        Scanner scanner = new Scanner(tempResultWithoutCloudBot.toString());
        int day = 1;
        LocalTime time = LocalTime.of(0, 0);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            Matcher matcher = PATTERN_FOR_FINAL_RESULT.matcher(line);
            if (matcher.find()) {
                LocalTime currentTime = LocalTime.parse(matcher.group(1));
                String operation = matcher.group(2);

                if (currentTime.isBefore(time)) {
                    day++;
                }
                time = currentTime;
                result.append(day).append(",");
                result.append(currentTime).append(",");
                result.append(shortenOperation(operation)).append("\n");
            }
        }
        scanner.close();
    }

    private static String shortenOperation(String operation) {
        for (Pattern pattern : OPERATIONS) {
            Matcher matcher = pattern.matcher(operation);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return operation;
    }
}
