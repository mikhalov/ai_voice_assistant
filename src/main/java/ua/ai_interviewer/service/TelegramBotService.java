package ua.ai_interviewer.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.WebClient;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ua.ai_interviewer.dto.chatgpt.ChatMessage;
import ua.ai_interviewer.dto.chatgpt.StreamResponse;
import ua.ai_interviewer.enums.Role;
import ua.ai_interviewer.exception.*;
import ua.ai_interviewer.model.Interview;
import ua.ai_interviewer.service.impl.AsyncOpenAIServiceImpl;
import ua.ai_interviewer.util.AudioConverter;
import ua.ai_interviewer.util.WebClientUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
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
    private final AsyncOpenAIServiceImpl asyncOpenAIService;
    private final ObjectMapper objectMapper;
    private final InterviewService interviewService;
    private final OpenAiService openAIService;
    private final WebClient webClient;
    private final String botToken;
    @Value("${telegram.bot.username}")
    private String botUsername;


    @Autowired
    public TelegramBotService(AsyncOpenAIServiceImpl asyncOpenAIService,
                              ObjectMapper objectMapper,
                              WebClient webClient,
                              OpenAiService openAIService,
                              InterviewService interviewService,
                              @Value("${telegram.bot.token}") String botToken) {
        super(botToken);
        this.asyncOpenAIService = asyncOpenAIService;
        this.objectMapper = objectMapper;
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
            processVoiceAndGetAnswerFromChatAsync(message);
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

    private void processVoiceAndGetAnswerFromChatAsync(Message message) {
        Long chatId = message.getChatId();
        sendMessage(chatId, "Processing your voice. Wait.",
                message.getMessageId());
        File mp3 = null;
        Interview interview = interviewService.getActiveIfExistOrCreateByChatId(chatId);
        try {
            mp3 = processVoice(message)
                    .orElseThrow(FileNotFoundException::new);
            String transcribed = openAIService.transcribe(mp3).text();
            interview.addMessage(openAIService.createMessage(transcribed));
            List<ChatMessage> conversation = interview.getConversation();

            sendConversationToChatGptAndResponseToUser(chatId, message.getMessageId(), interview, conversation);
        } catch (Exception e) {
            handleError(e, chatId, message.getMessageId());
        } finally {
            safeDeleteFile(mp3);
        }
    }

    private void sendConversationToChatGptAndResponseToUser(
            Long chatId,
            Integer responseToMessageId,
            Interview interview,
            List<ChatMessage> conversation) {
        StringBuilder response = new StringBuilder();
        int messageId = sendMessage(chatId, "...", responseToMessageId);

        Mono<Void> chatResponseHandler = asyncOpenAIService.getResponseFromChatGpt(conversation)
                .timeout(Duration.ofSeconds(100),
                        Flux.error(new OpenAIRequestException("Reached timeout of request")))
                .doOnNext(event -> {
                    log.trace("{}", event.data());
                    if (event.data() == null || event.data().equals("[DONE]")) {
                        return;
                    }
                    StreamResponse streamResponse = handleEvent(event);
                    String content = streamResponse.choices()
                            .stream()
                            .map(choice -> choice.delta().content())
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining());
                    if (content.isEmpty()) {
                        return;
                    }
                    response.append(content);
                })
                .doOnError(error -> handleError(error, chatId, messageId))
                .doOnComplete(() -> {
                    sendEditMessage(chatId, response.toString(), messageId);
                    log.debug("Stream completed");
                    interview.addMessage(ChatMessage.builder()
                            .role(Role.ASSISTANT.value)
                            .content(response.toString())
                            .build());
                    interviewService.update(interview);
                })
                .then();

        Flux<Long> intervalFlux = Flux.interval(Duration.ofSeconds(2))
                .doOnNext(tick -> sendEditMessage(chatId, response.toString(), messageId))
                .doOnError(error -> log.error("Error occurred", error));

        intervalFlux.takeUntilOther(chatResponseHandler)
                .retryWhen(WebClientUtil.retryAfterTooManyRequests())
                .subscribe();
    }

    // feature for java 20
//    private void handleError(Throwable error, Long chatId, Integer messageId) {
//        String chatResponse;
//        switch (error) {
//            case OpenAIRequestException e -> {
//                chatResponse = "Error occurred during AI request, you can try forward voice";
//                sendMessage(chatId, chatResponse, messageId);
//                log.error("{}", chatResponse, e);
//            } case TooManyRequestsException e -> {
//                chatResponse = "Too many requests, retrying fallen";
//                sendMessage(chatId, chatResponse, messageId);
//                log.error("{}", chatResponse, e);
//            } case TokenLimitExceptions e -> {
//                chatResponse = "Token limit has been reached, you can reset conversation";
//                sendMessage(chatId, chatResponse, messageId);
//                log.error("{}", chatResponse, e);
//            } case IOException e -> {
//                chatResponse = "Error occurred during file processing, you can try forward voice";
//                sendMessage(chatId, chatResponse, messageId);
//                log.error("{}", chatResponse, e);
//            } default -> {
//                chatResponse = "Unexpected error";
//                sendMessage(chatId, chatResponse, messageId);
//                log.error("{}", chatResponse, error);
//            }
//        }
//    }
    private void handleError(Throwable error, Long chatId, Integer messageId) {
        String chatResponse;

        if (error instanceof OpenAIRequestException) {
            chatResponse = "Error occurred during AI request, you can try forward voice";
        } else if (error instanceof TooManyRequestsException) {
            chatResponse = "Too many requests, retrying fallen";
        } else if (error instanceof TokenLimitExceptions) {
            chatResponse = "Token limit has been reached, you can reset conversation";
        } else if (error instanceof IOException) {
            chatResponse = "Error occurred during file processing, you can try forward voice";
        } else if (error instanceof UnauthorizedExeption) {
            chatResponse = "Unauthorized request";
        } else {
            chatResponse = "Unexpected error";
        }

        sendMessage(chatId, chatResponse, messageId);
        log.error("{}", chatResponse, error);
    }

    private void sendEditMessage(Long chatId, String response, int messageId) {
        try {
            EditMessageText editMessageText = new EditMessageText();
            editMessageText.setChatId(chatId);
            editMessageText.setMessageId(messageId);
            editMessageText.setText(response);

            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.error(
                    "Error during send edit message chat id {}, message id {}",
                    chatId, messageId, e
            );
            throw new MessageSendingException("Failed to send message to chatId: " + chatId, e);
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

    private int sendMessage(long chatId, String messageText, Integer replyToMessageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText);
        if (replyToMessageId != null) {
            message.setReplyToMessageId(replyToMessageId);
        }
        try {
            return execute(message).getMessageId();
        } catch (TelegramApiException e) {
            log.error(
                    "Error while sending message: '{}', user chat id '{}'",
                    messageText, chatId, e
            );
            throw new MessageSendingException("Failed to send message to chatId: " + chatId, e);
        }
    }

    private int sendMessage(long chatId, String messageText) {
        return sendMessage(chatId, messageText, null);
    }

    private StreamResponse handleEvent(ServerSentEvent<String> event) {
        String data = event.data();
        try {
            log.trace("Received event: {}", data);
            return objectMapper.readValue(data, StreamResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON: " + e.getMessage());
            throw new EventHandlingException("Failed to handle event: " + data);
        }
    }
}