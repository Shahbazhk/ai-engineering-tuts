package dev.aieng.p10;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ReAct (Yao et al., 2022): the model alternates Thought -> Action -> Observation until it gives a Final Answer.
 * This text protocol predates native tool calling and makes every step visible; native tool calling (Phase 6)
 * is the same loop with structured messages instead of parsed text.
 */
public final class ReactAgent {

    public enum StopReason { ANSWERED, MAX_STEPS, REPEATED_ACTION, TOKEN_BUDGET }

    public record Step(String thought, ToolRegistry.Call call, String observation) {
    }

    public record Result(String answer, StopReason reason, List<Step> steps) {
    }

    static final String SYSTEM = """
            You solve tasks step by step using tools.
            Tools:
            %s
            Respond in EXACTLY one of these two formats:

            Thought: <your reasoning>
            Action: <tool name>
            Action Input: <JSON object with the tool parameters>

            or, when you know the answer:

            Thought: <your reasoning>
            Final Answer: <answer for the user>""";

    private static final Pattern ACTION = Pattern.compile("(?s)Thought:\\s*(.*?)\\s*Action:\\s*(\\S+)\\s*Action Input:\\s*(\\{.*?})\\s*$");
    private static final Pattern FINAL = Pattern.compile("(?s)(?:Thought:\\s*(.*?)\\s*)?Final Answer:\\s*(.*)$");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Llm llm;
    private final ToolRegistry tools;
    private final int maxSteps;
    private final int maxTranscriptChars;

    public ReactAgent(Llm llm, ToolRegistry tools, int maxSteps, int maxTranscriptChars) {
        this.llm = llm;
        this.tools = tools;
        this.maxSteps = maxSteps;
        this.maxTranscriptChars = maxTranscriptChars;
    }

    // region loop
    public Result run(String task) {
        List<Llm.Message> transcript = new ArrayList<>();
        transcript.add(Llm.Message.system(SYSTEM.formatted(tools.describe())));
        transcript.add(Llm.Message.user(task));
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < maxSteps; i++) {
            if (size(transcript) > maxTranscriptChars) {
                return new Result("Stopped: the task needs more context than the budget allows.", StopReason.TOKEN_BUDGET, steps);
            }
            String reply = llm.next(List.copyOf(transcript));
            transcript.add(Llm.Message.assistant(reply));

            Matcher fin = FINAL.matcher(reply);
            Matcher act = ACTION.matcher(reply);
            if (act.find()) {
                ToolRegistry.Call call = new ToolRegistry.Call(act.group(2), parseArgs(act.group(3)));
                if (!steps.isEmpty() && steps.getLast().call() != null && steps.getLast().call().equals(call)) {
                    // The same call twice in a row returns the same observation: a loop, not progress.
                    return new Result("Stopped: repeated the same action without progress.", StopReason.REPEATED_ACTION, steps);
                }
                String observation = tools.invoke(call);
                steps.add(new Step(act.group(1), call, observation));
                transcript.add(Llm.Message.observation(observation));
            } else if (fin.find()) {
                return new Result(fin.group(2).strip(), StopReason.ANSWERED, steps);
            } else {
                // Format errors are fed back like tool errors: the model usually corrects itself.
                String observation = "ERROR: reply did not follow the required format.";
                steps.add(new Step(reply, null, observation));
                transcript.add(Llm.Message.observation(observation));
            }
        }
        return new Result("Stopped: step limit reached before an answer.", StopReason.MAX_STEPS, steps);
    }
    // endregion

    static Map<String, String> parseArgs(String json) {
        Map<String, String> args = new HashMap<>();
        try {
            JsonNode node = JSON.readTree(json);
            node.properties().forEach(e -> args.put(e.getKey(), e.getValue().asString()));
        } catch (RuntimeException e) {
            args.put("__invalid_json__", json);
        }
        return args;
    }

    private static int size(List<Llm.Message> transcript) {
        return transcript.stream().mapToInt(m -> m.content().length()).sum();
    }
}
