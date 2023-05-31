package ua.ai_interviewer.service;

import ua.ai_interviewer.dto.chatgpt.СhatMessage;
import ua.ai_interviewer.dto.chatgpt.ChatGPTResponse;
import ua.ai_interviewer.dto.wisper.WisperResponse;
import ua.ai_interviewer.exception.OpenAIRequestException;
import ua.ai_interviewer.exception.TooManyRequestsException;

import java.io.File;
import java.util.List;

public interface OpenAIService {

    ChatGPTResponse search(List<СhatMessage> conversation) throws OpenAIRequestException, TooManyRequestsException;

    WisperResponse transcribe(File file) throws OpenAIRequestException, TooManyRequestsException;

    СhatMessage createMessage(String content);

}

