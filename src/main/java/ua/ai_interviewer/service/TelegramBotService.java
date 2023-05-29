package ua.ai_interviewer.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ua.ai_interviewer.dto.chatgpt.ChatGPTResponse;
import ua.ai_interviewer.util.AudioConverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TelegramBotService extends TelegramLongPollingBot {

    private static final String FILE_URI_TEMPLATE = "https://api.telegram.org/file/bot%s/%s";
    private final ChatGPTService chatGPTService;
    private final AudioConverter audioConverter;
    private final WebClient webClient;
    private final String botToken;
    @Value("${telegram.bot.username}")
    private String botUsername;


    @Autowired
    public TelegramBotService(WebClient webClient,
                              AudioConverter audioConverter,
                              ChatGPTService chatGPTService,
                              @Value("${telegram.bot.token}") String botToken) {
        super(botToken);
        this.webClient = webClient;
        this.audioConverter = audioConverter;
        this.chatGPTService = chatGPTService;
        this.botToken = botToken;
    }

    @PostConstruct
    private void postConstruct() {
        initCommands();
    }


    private void initCommands() {
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/hello", "say hello to bot"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage()) {
            Message message = update.getMessage();
            if (message.hasText()) {
                executeMessage(update);
            } else if (message.hasVoice()) {
                executeVoiceAndGetAnswerFromChat(message);
            }
        }

    }

    private void executeVoiceAndGetAnswerFromChat(Message message) {
        java.io.File mp3 = executeVoice(message);
        String transcribed = chatGPTService.transcribe(mp3);
        ChatGPTResponse search = chatGPTService.search(transcribed);
        String collected = search.getChoices()
                .stream()
                .map(c -> c.getMessage().getContent())
                .collect(Collectors.joining());
        sendMessage(message.getChatId(), collected);
    }

    private java.io.File executeVoice(Message message) {
        String fileId = message.getVoice().getFileId();

        try {
            GetFile getFile = new GetFile();
            getFile.setFileId(fileId);
            File file = execute(getFile);
            String filePath = file.getFilePath();
            String fileUrl = String.format(FILE_URI_TEMPLATE, botToken, filePath);
            String uuid = UUID.randomUUID().toString();
            java.io.File ogg = saveVoice(fileUrl, uuid);

            return audioConverter.convertToMp3(ogg, uuid);
        } catch (IOException | TelegramApiException e) {
            log.error("An error has occurred while saving the voice" ,e);
            e.printStackTrace();
        }
        return null;
    }

    private java.io.File saveVoice(String fileUrl, String name) throws IOException {
        byte[] voice = Optional
                .ofNullable(webClient.get()
                        .uri(fileUrl)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block())
                .orElseThrow();
        Path path = Paths.get(String.format("%s.ogg", name));
        return Files.write(path, Objects.requireNonNull(voice))
                .toFile();
    }

    private void executeMessage(Update update) {
        Long chatId = update.getMessage().getChatId();
        String inputText = update.getMessage().getText();
        log.trace("chat id '{}', message: {}", chatId, inputText);
        switch (inputText) {
            case "/hello" -> sendMessage(chatId, "welcome");
            default -> sendMessage(chatId, "wrong command");
        }
    }

    private void sendMessage(long chatId, String messageText) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(
                    "error while sending message: '{}', user chat id '{}'",
                    messageText, chatId, e
            );
        }
    }
}