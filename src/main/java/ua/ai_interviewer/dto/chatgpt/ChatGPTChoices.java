package ua.ai_interviewer.dto.chatgpt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@RequiredArgsConstructor
@ToString
public class ChatGPTChoices {

    private ChatGPTMessages message;
}
