package ua.ai_interviewer.dto.chatgpt;

import lombok.*;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class ChatMessage {

    private String role;
    private String content;
}
