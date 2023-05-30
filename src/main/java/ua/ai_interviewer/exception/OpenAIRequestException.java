package ua.ai_interviewer.exception;

public class OpenAIRequestException extends RuntimeException {
    public OpenAIRequestException(String message) {
        super(message);
    }
}
