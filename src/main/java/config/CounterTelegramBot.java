package config;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;

import java.io.FileOutputStream;
import java.util.Random;


@Slf4j
@Component
public class CounterTelegramBot extends TelegramLongPollingBot {

    @Override
    public String getBotUsername() {
        return "ElleGismeteobot";
    }

    @Override
    public String getBotToken() {
        return "6100973681:AAFwHzRtRMVIGoJvNJqUcToG4k26zxOB5gU";
    }

    @Override
    public void onUpdateReceived(Update update) {

        if(update.hasMessage() && update.getMessage().hasText()){
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            String memberName = update.getMessage().getFrom().getFirstName();

            switch (messageText){
                case "/start":
                    startBot(chatId, memberName);
                    break;
                case "/weather":
                    try {
                        weatherBot(chatId, memberName);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                case "/photo":
                    photoWindow(chatId, memberName);
                    break;
                default:
                    log.info("Unexpected message");
                    unknownMessage(chatId, memberName);
            }
        }
    }

    //начало бота (start)
    private void startBot(long chatId, String userName) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Привет, " + userName + "! Я Elle бот, что я умею: \n " +
                "/weather - погода с Gismeteo \n" +
                "/photo - картинка для рабочего стола");

        try {
            execute(message);
            log.info("Reply sent");
        } catch (TelegramApiException e){
            log.error(e.getMessage());
        }

    }

    private void unknownMessage(long chatId, String userName) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Привет, для того, чтобы начать нажми /start");

        try {
            execute(message);
            log.info("Reply sent");
        } catch (TelegramApiException e){
            log.error(e.getMessage());
        }

    }


    //погода (weather)
    private void weatherBot (long chatId, String userName) throws IOException {
        //ПАРСИНГ СТРАНИЦЫ С ПОГОДОЙ
        Element gradus = null;
        Element weather = null;
        Element feelWeather = null;
        Element wind = null;
        Element pressure = null;
        Element humidity = null;
        Element water = null;
        try {
            var document = Jsoup.connect("https://www.gismeteo.ru/").get();
            //градус
            gradus = document.select("span[class=unit unit_temperature_c]").first();
            //описание погоды (ясно, пасмурно...)
            weather = document.select("div[class=weather-description]").first();
            //по ощущению
            feelWeather = document.select("span[class=unit unit_temperature_c]").get(1);
            //ветер
            wind = document.select("span[class=unit unit_wind_m_s]").first();
            //давление
            pressure = document.select("span[class=unit unit_pressure_mm_hg_atm]").first();
            //влажность
            humidity = document.select("div[class=item-value]").get(3);
            //Вода
            water = document.select("span[class=unit unit_temperature_c]").get(2);

        } catch (Exception e) {
            e.printStackTrace();
        }

        String gradusStr = gradus.text();
        String weatherStr = weather.text();
        String feelWeatherStr = feelWeather.text();
        String windStr = wind.text();
        String pressureStr = pressure.text();
        String humidityStr = humidity.text();
        String waterStr = water.text();

        //СОЗДАНИЕ EXCEL И ЗАПИСЬ ДАННЫХ

        //создание excel
        XSSFWorkbook workbook = new XSSFWorkbook();

        //создание нового листа в файле
        XSSFSheet sheet = workbook.createSheet("Погода");

        //Запись данных в ячейки (первая строка)
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Описание");
        headerRow.createCell(1).setCellValue("Градусы");
        headerRow.createCell(2).setCellValue("По ощущению");
        headerRow.createCell(3).setCellValue("Ветер");
        headerRow.createCell(4).setCellValue("Давление");
        headerRow.createCell(5).setCellValue("Влажность");
        headerRow.createCell(6).setCellValue("Вода");

        //Запись данных в ячейки (вторая строка)
        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue(weatherStr);
        row1.createCell(1).setCellValue(gradusStr);
        row1.createCell(2).setCellValue(feelWeatherStr);
        row1.createCell(3).setCellValue(windStr);
        row1.createCell(4).setCellValue(pressureStr);
        row1.createCell(5).setCellValue(humidityStr);
        row1.createCell(6).setCellValue(waterStr);

        //Запись в Excel-файл
        String filePath = "src/main/resources/weather.xlsx";
        FileOutputStream outputStream = new FileOutputStream(filePath);
        workbook.write(outputStream);
        workbook.close();
        outputStream.close();

        //Отправка файла в telegram

        SendDocument sendDocumentRequest = new SendDocument();

        sendDocumentRequest.setChatId(chatId);
        sendDocumentRequest.setDocument(new InputFile(new File(filePath)));
        String caption = "Файл с погодой";
        sendDocumentRequest.setCaption(caption);;
        try {
            // Execute the method
            execute(sendDocumentRequest);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        //Удаление файла локально

        File file = new File("src/main/resources/weather.xlsx");
        file.delete();
    }

    //фото для рабочего стола (photoWindow)
    private void photoWindow(long chatId, String userName) {
        // Create send method
        SendPhoto sendPhotoRequest = new SendPhoto();
        // Set destination chat id
        sendPhotoRequest.setChatId(chatId);
        // Set the photo url as a simple photo
        Random random = new Random();
        //выбор фото (рандомайзер выдачи картинки)
        int numbPhoto = random.nextInt(5);

        if (numbPhoto==0){
            sendPhotoRequest.setPhoto(new InputFile("https://i.7fon.org/1000/m615532.jpg"));
        } else if (numbPhoto==1) {
            sendPhotoRequest.setPhoto(new InputFile("https://i.7fon.org/1000/f64765913.jpg"));
        } else if (numbPhoto==2) {
            sendPhotoRequest.setPhoto(new InputFile("https://i.7fon.org/1000/z160770.jpg"));
        } else if (numbPhoto==3) {
            sendPhotoRequest.setPhoto(new InputFile("https://i.7fon.org/1000/d1005472.jpg"));
        } else if (numbPhoto==4) {
            sendPhotoRequest.setPhoto(new InputFile("https://i.7fon.org/1000/n33019.jpg"));
        }

        try {
            execute(sendPhotoRequest);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
