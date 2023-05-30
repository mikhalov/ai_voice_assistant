package ua.ai_interviewer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ua.ai_interviewer.dto.chatgpt.ChatGPTChoices;
import ua.ai_interviewer.service.OpenAIService;

import java.util.List;

@RestController
@RequestMapping()
public class ChatGTPController {

    private final OpenAIService openAIService;

    @Autowired
    public ChatGTPController(OpenAIService openAIService) {
        this.openAIService = openAIService;
    }

    @GetMapping
    public ResponseEntity<List<ChatGPTChoices>> search(@RequestParam String search) {
        var response = openAIService.search(search);
        return new ResponseEntity<>(response.getChoices(), response.getHttpStatus());
    }
}
