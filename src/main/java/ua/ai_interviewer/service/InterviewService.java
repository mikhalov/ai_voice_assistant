package ua.ai_interviewer.service;

import ua.ai_interviewer.enums.Language;
import ua.ai_interviewer.exception.InterviewNotFoundException;
import ua.ai_interviewer.model.Interview;

import java.util.Optional;

public interface InterviewService {


    Interview getActiveIfExistOrCreateByChatId(Long chatId);

    void update(Interview interview);

    Optional<Interview> getActiveByChatId(Long chatId) throws InterviewNotFoundException;

    Interview create(Long chatId, Language language, boolean speeching);
}
