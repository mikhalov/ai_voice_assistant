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
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ua.ai_interviewer.exception.OpenAIRequestException;
import ua.ai_interviewer.exception.TooManyRequestsException;
import ua.ai_interviewer.util.AudioConverter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TelegramBotService extends TelegramLongPollingBot {

    private static final String FILE_URI_TEMPLATE = "https://api.telegram.org/file/bot%s/%s";
    private final OpenAIService openAIService;
    private final AudioConverter audioConverter;
    private final WebClient webClient;
    private final String botToken;
    @Value("${telegram.bot.username}")
    private String botUsername;


    @Autowired
    public TelegramBotService(WebClient webClient,
                              AudioConverter audioConverter,
                              OpenAIService openAIService,
                              @Value("${telegram.bot.token}") String botToken) {
        super(botToken);
        this.webClient = webClient;
        this.audioConverter = audioConverter;
        this.openAIService = openAIService;
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
        log.trace("Received new update");
        if (update.hasMessage()) {
            CompletableFuture.runAsync(
                    () -> processMessage(update),
                    Executors.newFixedThreadPool(20)
            );
        }

    }

    private void processMessage(Update update) {
        Message message = update.getMessage();
        if (message.hasText()) {
            processText(message);
        } else if (message.hasVoice()) {
            processVoiceAndGetAnswerFromChat(message);
        }
    }

    private void processVoiceAndGetAnswerFromChat(Message message) {
        String chatResponse = "Unexpected error";
        sendMessage(message.getChatId(), "Processing your voice. Wait.",
                message.getMessageId());
        File mp3 = null;
        try {
            mp3 = processVoice(message)
                    .orElseThrow(FileNotFoundException::new);
            String transcribed = openAIService.transcribe(mp3).text();
            chatResponse = openAIService.search(transcribed)
                    .getChoices()
                    .stream()
                    .map(c -> c.getMessage().getContent())
                    .collect(Collectors.joining());
            log.trace("Got response text {}", chatResponse);
        } catch (OpenAIRequestException e) {
            chatResponse = "Error occurred during AI request, you can try forward voice";
            log.error(chatResponse, e);
        } catch (TooManyRequestsException e) {
            chatResponse = "Too many requests, retry has fallen, you can try forward voice";
            log.error(chatResponse, e);
        } catch (IOException e) {
            chatResponse = "Error occurred during file processing, you can try forward voice";
            log.error(chatResponse, e);
        } finally {
            sendMessage(message.getChatId(), chatResponse, message.getMessageId());
            safeDeleteFile(mp3);
        }
    }

    private void safeDeleteFile(File file) {
        if (file != null) try {
            Files.delete(file.toPath());
            log.debug("Successful deleted file {}", file.getName());
        } catch (IOException e) {
            log.error("Error during deleting file", e);
        }
    }

    private Optional<File> processVoice(Message message) throws IOException {
        String fileId = message.getVoice().getFileId();

        try {
            GetFile getFile = new GetFile();
            getFile.setFileId(fileId);
            org.telegram.telegrambots.meta.api.objects.File voice = execute(getFile);
            String filePath = voice.getFilePath();
            String fileUrl = String.format(FILE_URI_TEMPLATE, botToken, filePath);
            String uuid = UUID.randomUUID().toString();
            File ogg = saveVoice(fileUrl, uuid);
            Optional<File> fileOptional = Optional.ofNullable(audioConverter.convertToMp3(ogg, uuid));
            safeDeleteFile(ogg);

            return fileOptional;
        } catch (TelegramApiException e) {
            log.error("An error has occurred while execute the voice", e);
        }
        return Optional.empty();
    }

    private File saveVoice(String fileUrl, String name) throws IOException {
        byte[] voice = Optional.ofNullable(
                webClient.get()
                        .uri(fileUrl)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block()
        ).orElseThrow();
        Path path = Paths.get(String.format("%s.ogg", name));
        return Files.write(path, Objects.requireNonNull(voice))
                .toFile();
    }

    private void processText(Message message) {
        Long chatId = message.getChatId();
        String inputText = message.getText();
        log.trace("chat id '{}', message: {}", chatId, inputText);
        switch (inputText) {
            case "/hello" -> sendMessage(chatId, "welcome");
            default -> sendMessage(chatId, "wrong command");
        }
    }

    private void sendMessage(long chatId, String messageText, Integer replyToMessageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText);
        if (replyToMessageId != null) {
            message.setReplyToMessageId(replyToMessageId);
        }
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(
                    "Error while sending message: '{}', user chat id '{}'",
                    messageText, chatId, e
            );
        }
    }

    private void sendMessage(long chatId, String messageText) {
        sendMessage(chatId, messageText, null);
    }
}