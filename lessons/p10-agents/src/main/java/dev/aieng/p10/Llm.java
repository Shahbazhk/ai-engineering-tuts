package dev.aieng.p10;

import java.util.List;

/**
 * The only thing an agent needs from a model: given the transcript so far, produce the next assistant message.
 * In production this wraps a ChatClient call; in tests it is a scripted lambda.
 */
@FunctionalInterface
public interface Llm {

    record Message(String role, String content) {
        public static Message system(String c) {
            return new Message("system", c);
        }

        public static Message user(String c) {
            return new Message("user", c);
        }

        public static Message assistant(String c) {
            return new Message("assistant", c);
        }

        public static Message observation(String c) {
            return new Message("user", "Observation: " + c);
        }
    }

    String next(List<Message> transcript);
}
