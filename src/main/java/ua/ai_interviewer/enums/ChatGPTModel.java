package ua.ai_interviewer.enums;

import lombok.Getter;

@Getter
public enum ChatGPTModel {

    GPT_TURBO("gpt-3.5-turbo"),
    WISPER("whisper-1");

    private String value;

    ChatGPTModel(String value) {
        this.value = value;
    }
}
