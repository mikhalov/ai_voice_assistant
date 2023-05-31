package ua.ai_interviewer.dto.chatgpt;

import java.util.List;


public record StreamResponse(List<StreamChoice> choices) {
}
