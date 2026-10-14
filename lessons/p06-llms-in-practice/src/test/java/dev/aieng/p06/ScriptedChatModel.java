package dev.aieng.p06;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import reactor.core.publisher.Flux;

/**
 * A fake Spring AI ChatModel for tests: each call is answered by the next scripted function, and every prompt
 * is recorded so tests can assert exactly what the application sent. This is the most valuable test double in
 * AI engineering - it makes prompt assembly, tool loops and parsing deterministic and free.
 */
final class ScriptedChatModel implements ChatModel {

    final List<Prompt> prompts = new ArrayList<>();
    private final List<Function<Prompt, AssistantMessage>> script = new ArrayList<>();
    private List<String> streamChunks = List.of();

    ScriptedChatModel reply(String text) {
        script.add(p -> new AssistantMessage(text));
        return this;
    }

    ScriptedChatModel reply(Function<Prompt, AssistantMessage> fn) {
        script.add(fn);
        return this;
    }

    ScriptedChatModel streamChunks(String... chunks) {
        streamChunks = List.of(chunks);
        return this;
    }

    /**
     * Real provider models return tool-capable options; ChatClient copies tool definitions into them. A fake must
     * do the same, or the tool-calling advisor has nowhere to put the tools.
     */
    @Override
    public ChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    @Override
    public ChatOptions getOptions() {
        return getDefaultOptions();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        if (script.isEmpty()) {
            throw new IllegalStateException("no scripted reply left for prompt: " + prompt.getContents());
        }
        return new ChatResponse(List.of(new Generation(script.removeFirst().apply(prompt))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        prompts.add(prompt);
        return Flux.fromIterable(streamChunks)
                .map(chunk -> new ChatResponse(List.of(new Generation(new AssistantMessage(chunk)))));
    }

    Prompt last() {
        return prompts.getLast();
    }
}
