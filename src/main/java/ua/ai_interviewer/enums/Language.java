package ua.ai_interviewer.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Language {
    ENGLISH("en-US", "en-US-Standard-B", "en"),
    RUSSIAN("ru-RU", "ru-RU-Standard-D", "ru"),
    UKRAINIAN("uk-UA", "uk-UA-Standard-A", "uk");

    private final String code;
    private final String name;
    private final String iso;
}
