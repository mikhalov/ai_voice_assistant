package ua.ai_interviewer.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import ua.ai_interviewer.model.Interview;

import java.util.Optional;

@Repository
public interface InterviewRepository extends MongoRepository<Interview, String> {

    Optional<Interview> findByChatIdAndActiveTrue(Long chatId);
}
