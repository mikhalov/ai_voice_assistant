package ua.ai_interviewer.model;

import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import ua.ai_interviewer.dto.chatgpt.СhatMessage;

import java.util.List;
import java.util.NoSuchElementException;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Document
public class Interview {

    @MongoId
    private String id;
    private Long chatId;
    private boolean active;
    private List<СhatMessage> conversation;

    public void addMessage(СhatMessage message) {
        if (conversation != null) {
            conversation.add(message);
        } else {
            throw new NoSuchElementException();
        }
    }

}
