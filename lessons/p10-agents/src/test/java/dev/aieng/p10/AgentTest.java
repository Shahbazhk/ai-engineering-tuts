package dev.aieng.p10;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.aieng.p10.ToolRegistry.Risk;
import dev.aieng.p10.ToolRegistry.Tool;

class AgentTest {

    final List<String> executed = new ArrayList<>();

    ToolRegistry registry(ToolRegistry.Approver approver) {
        return new ToolRegistry(approver)
                .register(new Tool("ticket_status", "Status of a support ticket", List.of("id"), Risk.READ_ONLY, a -> {
                    executed.add("ticket_status " + a.get("id"));
                    return "TICKET-" + a.get("id") + ": OPEN, assigned to the portal team, waiting for customer logs";
                }))
                .register(new Tool("close_ticket", "Close a support ticket", List.of("id"), Risk.SIDE_EFFECT, a -> {
                    executed.add("close_ticket " + a.get("id"));
                    return "closed";
                }));
    }

    static Llm scripted(String... replies) {
        Iterator<String> it = List.of(replies).iterator();
        return transcript -> it.next();
    }

    @Test
    void reactLoopUsesToolsThenAnswers() {
        List<List<Llm.Message>> seen = new ArrayList<>();
        Iterator<String> replies = List.of(
                "Thought: I need the ticket status.\nAction: ticket_status\nAction Input: {\"id\": \"42\"}",
                "Thought: It waits for logs.\nFinal Answer: Ticket 42 is open and waiting for your logs.").iterator();
        ReactAgent agent = new ReactAgent(t -> {
            seen.add(t);
            return replies.next();
        }, registry(c -> true), 5, 20_000);

        ReactAgent.Result r = agent.run("What is happening with ticket 42?");

        assertEquals(ReactAgent.StopReason.ANSWERED, r.reason());
        assertEquals("Ticket 42 is open and waiting for your logs.", r.answer());
        assertEquals(List.of("ticket_status 42"), executed);
        assertTrue(seen.get(0).getFirst().content().contains("- ticket_status[id]: Status of a support ticket"));
        assertTrue(seen.get(1).getLast().content().startsWith("Observation: TICKET-42: OPEN"), "observation fed back");
    }

    @Test
    void sideEffectsNeedApproval() {
        ReactAgent agent = new ReactAgent(scripted(
                "Thought: close it.\nAction: close_ticket\nAction Input: {\"id\": \"42\"}",
                "Thought: denied.\nFinal Answer: I could not close the ticket; a reviewer declined."),
                registry(call -> false), 5, 20_000);
        ReactAgent.Result r = agent.run("Close ticket 42");
        assertTrue(executed.isEmpty(), "the tool must not run without approval");
        assertTrue(r.steps().getFirst().observation().startsWith("DENIED"));
        assertEquals(ReactAgent.StopReason.ANSWERED, r.reason());
    }

    @Test
    void modelMistakesBecomeObservationsNotCrashes() {
        ToolRegistry tools = registry(c -> true);
        assertTrue(tools.invoke(new ToolRegistry.Call("drop_database", Map.of())).startsWith("ERROR: unknown tool"));
        assertTrue(tools.invoke(new ToolRegistry.Call("ticket_status", Map.of())).contains("missing required parameter 'id'"));
        ReactAgent agent = new ReactAgent(scripted("I think the answer is 42", "Final Answer: 42"), tools, 5, 20_000);
        ReactAgent.Result r = agent.run("?");
        assertEquals("ERROR: reply did not follow the required format.", r.steps().getFirst().observation());
        assertEquals("42", r.answer());
    }

    @Test
    void loopsAndBudgetsStopTheAgent() {
        String same = "Thought: check again.\nAction: ticket_status\nAction Input: {\"id\": \"1\"}";
        ReactAgent looping = new ReactAgent(t -> same, registry(c -> true), 10, 20_000);
        assertEquals(ReactAgent.StopReason.REPEATED_ACTION, looping.run("status?").reason());

        AtomicInteger n = new AtomicInteger();
        ReactAgent wandering = new ReactAgent(
                t -> "Thought: more.\nAction: ticket_status\nAction Input: {\"id\": \"" + n.incrementAndGet() + "\"}",
                registry(c -> true), 3, 20_000);
        ReactAgent.Result r = wandering.run("status?");
        assertEquals(ReactAgent.StopReason.MAX_STEPS, r.reason());
        assertEquals(3, r.steps().size());

        ReactAgent tiny = new ReactAgent(t -> same, registry(c -> true), 10, 50);
        assertEquals(ReactAgent.StopReason.TOKEN_BUDGET, tiny.run("status?").reason());
    }

    @Test
    void shortTermMemorySummarisesEvictedMessages() {
        Memory.ShortTerm memory = new Memory.ShortTerm(2, prompt -> "summary(" + prompt.length() + ")");
        memory.add(Llm.Message.user("My name is Sam"));
        memory.add(Llm.Message.assistant("Hi Sam"));
        memory.add(Llm.Message.user("I work on the portal"));
        List<Llm.Message> ctx = memory.context();
        assertEquals(3, ctx.size());
        assertTrue(ctx.getFirst().content().startsWith("Summary of earlier conversation: summary("));
        assertEquals("I work on the portal", ctx.getLast().content());
    }

    @Test
    void longTermMemoryIsScopedPerUser() {
        Memory.LongTerm memory = new Memory.LongTerm();
        memory.remember("sam", "Sam prefers answers in German");
        memory.remember("sam", "Sam is on the portal team");
        memory.remember("kim", "Kim prefers answers in French");
        assertEquals(List.of("Sam prefers answers in German"), memory.recall("sam", "which answers language preferences", 1));
        assertTrue(memory.recall("kim", "portal team", 5).isEmpty(), "never recall another user's memories");
    }

    @Test
    void workflowPatterns() {
        assertEquals("HELLO!", Workflows.chain("hello", List.of(String::toUpperCase, s -> s + "!"), s -> !s.isBlank()));
        assertThrows(IllegalStateException.class, () -> Workflows.chain("x", List.of(s -> ""), s -> !s.isBlank()));

        String routed = Workflows.route("refund please", s -> s.contains("refund") ? "billing" : "other",
                Map.of("BILLING", s -> "billing team"), s -> "general queue");
        assertEquals("billing team", routed);

        String votes = Workflows.parallel("text", List.of(s -> "safe", s -> "safe", s -> "unsafe"),
                rs -> rs.stream().filter("unsafe"::equals).count() > 0 ? "BLOCK" : "ALLOW");
        assertEquals("BLOCK", votes, "any guard voting unsafe blocks");

        String report = Workflows.orchestrate("compare A and B",
                t -> List.of("research A", "research B", "research C", "research D"),
                sub -> sub.toUpperCase(), rs -> String.join("; ", rs), 2);
        assertEquals("research A => RESEARCH A; research B => RESEARCH B", report, "planner output is capped");

        AtomicInteger drafts = new AtomicInteger();
        String text = Workflows.refine("write", t -> "draft" + drafts.incrementAndGet(),
                d -> new Workflows.Critique(d.equals("draft3"), "too short"), 5);
        assertEquals("draft3", text);
        assertEquals(3, drafts.get());
    }
}
