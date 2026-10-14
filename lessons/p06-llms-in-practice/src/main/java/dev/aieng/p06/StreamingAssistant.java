package dev.aieng.p06;

import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import reactor.core.publisher.Flux;

/**
 * Streaming with Spring AI returns a Reactor {@link Flux} of text chunks. In Spring MVC you can return it from a
 * controller with produces = TEXT_EVENT_STREAM_VALUE and Spring writes Server-Sent Events; in WebFlux the
 * same Flux flows end to end without blocking threads.
 */
public class StreamingAssistant {

    private final ChatClient chat;

    public StreamingAssistant(ChatModel model) {
        this.chat = ChatClient.create(model);
    }

    // region stream
    public Flux<String> stream(String question) {
        return chat.prompt()
                .user(question)
                .stream()
                .content()
                .timeout(Duration.ofSeconds(60)); // a stalled stream must not hold a connection forever
    }
    // endregion
}
