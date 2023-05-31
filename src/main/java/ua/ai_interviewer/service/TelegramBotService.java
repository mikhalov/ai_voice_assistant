package ua.ai_interviewer.service;


import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ua.ai_interviewer.dto.chatgpt.ChatMessage;
import ua.ai_interviewer.exception.OpenAIRequestException;
import ua.ai_interviewer.exception.TooManyRequestsException;
import ua.ai_interviewer.model.Interview;
import ua.ai_interviewer.util.AudioConverter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TelegramBotService extends TelegramLongPollingBot {

    private final ConcurrentHashMap<Long, Boolean> activeUsers = new ConcurrentHashMap<>();
    private final InterviewService interviewService;
    private final OpenAIService openAIService;
    private final WebClient webClient;
    private final String botToken;
    @Value("${telegram.bot.username}")
    private String botUsername;


    @Autowired
    public TelegramBotService(WebClient webClient,
                              OpenAIService openAIService,
                              InterviewService interviewService,
                              @Value("${telegram.bot.token}") String botToken) {
        super(botToken);
        this.webClient = webClient;
        this.openAIService = openAIService;
        this.interviewService = interviewService;
        this.botToken = botToken;
    }

    @PostConstruct
    private void postConstruct() {
        initCommands();
    }


    private void initCommands() {
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/hello", "say hello to bot"));
        listOfCommands.add(new BotCommand("/reset", "reset current conversation"));
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
            Message message = update.getMessage();
            if (!activeUsers.containsKey(message.getChatId())) {
                CompletableFuture.runAsync(() -> {
                    activeUsers.put(message.getChatId(), true);
                    processMessage(message);

                    activeUsers.remove(message.getChatId());
                });
            } else sendMessage(message.getChatId(),
                    """
                            Still processing your previously message.
                            You can forward it when it has been done""");
        }

    }

    private void processMessage(Message message) {

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
        Interview interview = interviewService.getActiveIfExistOrCreateByChatId(message.getChatId());
        try {
            mp3 = processVoice(message)
                    .orElseThrow(FileNotFoundException::new);
            String transcribed = openAIService.transcribe(mp3).text();
            interview.addMessage(openAIService.createMessage(transcribed));
            List<ChatMessage> conversation = interview.getConversation();
            var response = openAIService.search(conversation);
            log.debug("{}", response.toString());
            chatResponse = response.getChoices()
                    .stream()
                    .map(choice -> {
                        var m = choice.getMessage();
                        interview.addMessage(m);
                        return m.getContent();
                    })
                    .collect(Collectors.joining());
            log.trace("Got response text {}", chatResponse);
            interviewService.update(interview);
        } catch (OpenAIRequestException e) {
            chatResponse = "Error occurred during AI request, you can try forward voice";
            log.error("{}", chatResponse, e);
        } catch (TooManyRequestsException e) {
            chatResponse = "Too many requests, retry has fallen, you can try forward voice";
            log.error("{}", chatResponse, e);
        } catch (IOException e) {
            chatResponse = "Error occurred during file processing, you can try forward voice";
            log.error("{}", chatResponse, e);
        } catch (Exception e) {
            log.error("{}", chatResponse, e);
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
            String fileUniqueId = voice.getFileUniqueId();
            String fileUrl = voice.getFileUrl(botToken);
            log.debug("File path {}. File id {} . File unique id {}", filePath, voice.getFileId(), fileUniqueId);
            File ogg = saveVoice(fileUrl, fileUniqueId);
            Optional<File> fileOptional = Optional.of(AudioConverter.convertToMp3(ogg, fileUniqueId));
            safeDeleteFile(ogg);

            return fileOptional;
        } catch (TelegramApiException e) {
            log.error("An error has occurred while execute the voice", e);
        }
        return Optional.empty();
    }

    private File saveVoice(String fileUrl, String name) throws UncheckedIOException {
        String voicePath = String.format("%s%s", name, fileUrl.substring(fileUrl.lastIndexOf('.')));
        Path outputPath = Paths.get(voicePath);

        webClient.get()
                .uri(fileUrl)
                .exchangeToFlux(response -> response.body(BodyExtractors.toDataBuffers()))
                .doOnError(e -> log.error("Error during voice save", e))
                .doOnNext(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    try (FileOutputStream out = new FileOutputStream(outputPath.toFile(), true)) {
                        out.write(bytes);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .blockLast();

        return outputPath.toFile();
    }

    private void processText(Message message) {
        Long chatId = message.getChatId();
        String inputText = message.getText();
        log.debug("chat id '{}', message: {}", chatId, inputText);
        switch (inputText) {
            case "/hello" -> sendMessage(chatId, "welcome");
            case "/reset" -> resetConversation(chatId);
            default -> sendMessage(chatId, "wrong command");
        }
    }

    private void resetConversation(Long chatId) {
        interviewService.getActiveByChatId(chatId)
                .ifPresentOrElse(
                        interview -> {
                            interview.setActive(false);
                            interviewService.update(interview);
                            String message = "Conversation has been reset successful";
                            log.debug("{} for chat id {}", message, chatId);
                            sendMessage(chatId, message);
                        },
                        () -> sendMessage(chatId, "You do not have active conversation")
                );


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