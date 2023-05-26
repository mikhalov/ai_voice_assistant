package ua.ai_interviewer.enums;

import lombok.Getter;

@Getter
public enum Role {

    USER("user");

    public final String value;
    Role (String value) {
        this.value = value;
    }
}
