package ua.ai_interviewer.dto.telegram;

import org.telegram.telegrambots.meta.api.interfaces.BotApiObject;

public record UpdateContent(Long chatId, BotApiObject apiObject) {


}
