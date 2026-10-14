package dev.aieng.p06;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LangChain4j's declarative style: describe the AI capability as a Java interface and let AiServices generate
 * the implementation (a dynamic proxy - the same trick as Spring Data repositories and Feign clients).
 */
public final class LangChainAssistant {

    private LangChainAssistant() {
    }

    // region service
    public interface Translator {

        @SystemMessage("You translate technical documentation. Keep code, class names and URLs unchanged.")
        @UserMessage("Translate to {{language}}:\n{{text}}")
        String translate(@V("text") String text, @V("language") String language);
    }

    public enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE }

    public interface FeedbackAnalyzer {

        @UserMessage("Classify the sentiment of this customer feedback: {{it}}")
        Sentiment classify(String feedback); // return type drives output parsing - an enum here
    }

    public static Translator translator(ChatModel model) {
        return AiServices.create(Translator.class, model);
    }

    public static FeedbackAnalyzer analyzer(ChatModel model) {
        return AiServices.create(FeedbackAnalyzer.class, model);
    }
    // endregion
}
