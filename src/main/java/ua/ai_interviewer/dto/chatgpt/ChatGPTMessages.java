package ua.ai_interviewer.dto.chatgpt;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@ToString
public class ChatGPTMessages {

    private String role;
    private String content;

}
