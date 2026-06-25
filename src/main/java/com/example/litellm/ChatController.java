package com.example.litellm;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

/**
 * Demonstrates calling an LLM through a LiteLLM proxy.
 *
 * LiteLLM speaks the OpenAI wire protocol, so Spring AI's OpenAI ChatClient
 * works unchanged once the base-url points at the proxy (see application.yml).
 */
@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("You are a concise, helpful assistant.")
                .build();
    }

    /**
     * Simple synchronous chat.
     * Example: GET http://localhost:8080/chat?message=Hello
     */
    @GetMapping("/chat")
    public Map<String, String> chat(
            @RequestParam(defaultValue = "Tell me a fun fact about the Spring Framework.") String message) {
        String reply = chatClient.prompt()
                .user(message)
                .call()
                .content();
        return Map.of("reply", reply);
    }

    /**
     * Server-sent streaming chat (token-by-token).
     * Example: GET http://localhost:8080/stream?message=Write a haiku about Java
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
            @RequestParam(defaultValue = "Write a haiku about Java.") String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }

    /**
     * POST variant accepting a JSON body: { "message": "..." }
     */
    @PostMapping("/chat")
    public Map<String, String> chatPost(@RequestBody ChatRequest request) {
        String reply = chatClient.prompt()
                .user(request.message())
                .call()
                .content();
        return Map.of("reply", reply);
    }

    public record ChatRequest(String message) {
    }
}
