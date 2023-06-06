package ua.ai_interviewer.dto.wisper;

import org.springframework.core.io.FileSystemResource;

public record WisperRequest(FileSystemResource file, String model, String language) {
}
