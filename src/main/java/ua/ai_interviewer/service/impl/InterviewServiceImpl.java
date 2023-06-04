package ua.ai_interviewer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ua.ai_interviewer.model.Interview;
import ua.ai_interviewer.repository.InterviewRepository;
import ua.ai_interviewer.service.InterviewService;

import java.util.ArrayList;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private final InterviewRepository interviewRepository;

    @Override
    public Interview getActiveIfExistOrCreateByChatId(Long chatId) {
        return interviewRepository.findByChatIdAndActiveTrue(chatId)
                .orElseGet(() -> create(chatId));
    }

    @Override
    public void update(Interview interview) {
        interviewRepository.save(interview);
    }

    @Override
    public Optional<Interview> getActiveByChatId(Long chatId) {
        return interviewRepository.findByChatIdAndActiveTrue(chatId);
    }

    private Interview create(Long chatId) {
        return interviewRepository.save(
                Interview.builder()
                        .chatId(chatId)
                        .active(true)
                        .conversation(new ArrayList<>())
                        .build()
        );
    }
}
