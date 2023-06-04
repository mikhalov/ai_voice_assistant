package ua.ai_interviewer.dto.chatgpt;

import com.fasterxml.jackson.annotation.JsonProperty;


public record StreamChoice(@JsonProperty("finish_reason") String finishReason,
                           StreamDelta delta) {

}
