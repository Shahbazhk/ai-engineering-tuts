package dev.aieng.p06;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import reactor.test.StepVerifier;

class LlmPracticeTest {

    @Test
    void costModelRanksModelsAndAccountsForCaching() {
        var w = new TokenCostEstimator.Workload(1000, 1_000_000, 0, 0, 0);
        var cheap = new TokenCostEstimator.Model("cheap", 1, 1, 100, 0);
        assertEquals(1.0, TokenCostEstimator.estimate(cheap, w).costPerRequest(), 1e-9, "1M input tokens at $1/M");
        var cached = new TokenCostEstimator.Workload(1000, 1_000_000, 0, 0.5, 0.9);
        assertEquals(0.55, TokenCostEstimator.estimate(cheap, cached).costPerRequest(), 1e-9, "half cached at 90% off");
        assertEquals(2.0, TokenCostEstimator.estimate(new TokenCostEstimator.Model("m", 0, 0, 100, 1000),
                new TokenCostEstimator.Workload(1, 0, 100, 0, 0)).latencySeconds(), 1e-9);
        assertEquals(3, TokenCostEstimator.roughTokens("twelve chars"));
    }

    @Test
    void chatClientSendsTemplatedSystemPromptAndOptions() {
        ScriptedChatModel model = new ScriptedChatModel().reply("Restart the portal node.");
        String answer = new SupportAssistant(model, "Liferay DXP").answer("Cache is stale, what do I do?");
        assertEquals("Restart the portal node.", answer);
        List<Message> sent = model.last().getInstructions();
        assertEquals(MessageType.SYSTEM, sent.getFirst().getMessageType());
        assertTrue(sent.getFirst().getText().contains("support assistant for Liferay DXP"));
        assertTrue(sent.getFirst().getText().contains("at most 3 sentences"));
        assertEquals("Cache is stale, what do I do?", sent.getLast().getText());
        assertEquals(0.2, model.last().getOptions().getTemperature());
        assertEquals(400, model.last().getOptions().getMaxTokens());
    }

    @Test
    void promptTemplateRendersPlaceholders() {
        // StringTemplate emits the platform line separator; normalise so the test passes on Windows and Linux.
        String p = SupportAssistant.renderTicketPrompt("ACME", "Login fails").replace("\r\n", "\n");
        assertTrue(p.startsWith("Customer ACME wrote:"), p);
        assertTrue(p.contains("<ticket>\nLogin fails\n</ticket>"), p);
    }

    @Test
    void langChain4jAiServiceBuildsMessagesFromAnnotations() {
        RecordingLc4jModel model = new RecordingLc4jModel("Hallo Welt");
        String out = LangChainAssistant.translator(model).translate("Hello world", "German");
        assertEquals("Hallo Welt", out);
        assertTrue(model.last.getFirst() instanceof SystemMessage s && s.text().contains("Keep code"));
        assertTrue(((UserMessage) model.last.getLast()).singleText().startsWith("Translate to German:\nHello world"));
    }

    @Test
    void langChain4jParsesEnumReturnTypes() {
        var analyzer = LangChainAssistant.analyzer(new RecordingLc4jModel("NEGATIVE"));
        assertEquals(LangChainAssistant.Sentiment.NEGATIVE, analyzer.classify("The upgrade broke our SSO."));
    }

    @Test
    void streamingDeliversChunksInOrder() {
        ScriptedChatModel model = new ScriptedChatModel().streamChunks("Spring ", "AI ", "streams.");
        StepVerifier.create(new StreamingAssistant(model).stream("hi"))
                .expectNext("Spring ", "AI ", "streams.")
                .verifyComplete();
    }

    @Test
    void structuredOutputIsParsedIntoARecordAndValidated() {
        ScriptedChatModel model = new ScriptedChatModel().reply("""
                {"category":"TECHNICAL","priority":"HIGH","summary":"SSO login loop after upgrade",
                 "affectedProducts":["Portal","SSO"]}""");
        TicketTriage.Triage t = new TicketTriage(model).triage("After the 7.4 upgrade users loop on the SSO page");
        assertEquals(TicketTriage.Category.TECHNICAL, t.category());
        assertEquals(TicketTriage.Priority.HIGH, t.priority());
        assertEquals(List.of("Portal", "SSO"), t.affectedProducts());
        String userText = model.last().getUserMessage().getText();
        assertTrue(userText.contains("<ticket>"), userText);
        assertTrue(userText.toLowerCase().contains("json"), "format instructions are appended: " + userText);
        assertEquals(0.0, model.last().getOptions().getTemperature());

        ScriptedChatModel bad = new ScriptedChatModel().reply("{\"category\":\"BILLING\",\"priority\":null,\"summary\":\"x\"}");
        assertThrows(IllegalStateException.class, () -> new TicketTriage(bad).triage("refund?"));
    }

    @Test
    void toolCallingLoopExecutesTheMethodAndFeedsTheResultBack() {
        OrderTools tools = new OrderTools();
        ScriptedChatModel model = new ScriptedChatModel()
                // 1st model turn: ask the application to run a tool
                .reply(p -> AssistantMessage.builder().content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "orderStatus",
                                "{\"orderId\":\"A-1001\"}")))
                        .build())
                // 2nd model turn: read the tool result from the conversation and answer
                .reply(p -> {
                    ToolResponseMessage tr = (ToolResponseMessage) p.getInstructions().getLast();
                    String data = tr.getResponses().getFirst().responseData();
                    return new AssistantMessage(data.contains("SHIPPED") ? "Your order has shipped, ETA 2026-10-16." : "?");
                });

        String answer = OrderTools.ask(model, tools, "Where is my order A-1001?");

        assertEquals("Your order has shipped, ETA 2026-10-16.", answer);
        assertEquals(List.of("orderStatus(A-1001)"), tools.calls());
        assertEquals(2, model.prompts.size(), "one round trip for the tool call, one for the answer");
    }

    @Test
    void memoryAdvisorReplaysHistoryPerConversation() {
        ScriptedChatModel model = new ScriptedChatModel().reply("Nice to meet you, Sam.").reply("Your name is Sam.").reply("I don't know.");
        ConversationService chat = new ConversationService(model);
        chat.send("c1", "My name is Sam.");
        chat.send("c1", "What is my name?");
        List<String> secondPrompt = model.last().getInstructions().stream().map(Message::getText).toList();
        assertTrue(secondPrompt.contains("My name is Sam."), secondPrompt.toString());
        assertTrue(secondPrompt.contains("Nice to meet you, Sam."));
        assertEquals(4, chat.storedMessages("c1"));

        chat.send("c2", "What is my name?");
        assertEquals(1, model.last().getInstructions().size(), "another conversation sees no history");
    }

    /** A LangChain4j ChatModel test double: records the messages it receives and returns a fixed answer. */
    static final class RecordingLc4jModel implements ChatModel {
        final String answer;
        List<ChatMessage> last;

        RecordingLc4jModel(String answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            last = request.messages();
            return ChatResponse.builder().aiMessage(AiMessage.from(answer)).build();
        }
    }
}
