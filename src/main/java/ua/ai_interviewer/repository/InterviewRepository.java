package ua.ai_interviewer.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ua.ai_interviewer.model.Interview;

import java.util.Optional;

public interface InterviewRepository extends MongoRepository<Interview, String> {

    Optional<Interview> findByChatIdAndActiveTrue(Long chatId);
}
