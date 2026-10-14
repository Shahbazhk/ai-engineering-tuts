package dev.aieng.p10;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A registry of tools the agent may call - conceptually a small BeanFactory: tools are registered by name with
 * metadata (description, parameters, risk) and resolved at run time. Invocation is the trust boundary: arguments
 * come from the model, so they are validated here, and side effects require an approval decision.
 */
public final class ToolRegistry {

    public enum Risk { READ_ONLY, SIDE_EFFECT }

    public record Tool(String name, String description, List<String> requiredParams, Risk risk,
                       Function<Map<String, String>, String> function) {
    }

    public record Call(String tool, Map<String, String> args) {
    }

    /** Decides whether a side-effecting call may run (a human click, a policy engine, a dry-run mode...). */
    @FunctionalInterface
    public interface Approver {
        boolean approve(Call call);
    }

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Approver approver;

    public ToolRegistry(Approver approver) {
        this.approver = approver;
    }

    public ToolRegistry register(Tool tool) {
        if (tools.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalArgumentException("duplicate tool " + tool.name());
        }
        return this;
    }

    /** The tool catalogue as it appears in the prompt. Every word costs tokens on every step - keep it tight. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        tools.values().forEach(t -> sb.append("- ").append(t.name()).append(t.requiredParams()).append(": ")
                .append(t.description()).append(t.risk() == Risk.SIDE_EFFECT ? " (requires approval)" : "").append('\n'));
        return sb.toString();
    }

    // region invoke
    /** Never throws for model mistakes: errors are returned as observations so the agent can correct itself. */
    public String invoke(Call call) {
        Tool tool = tools.get(call.tool());
        if (tool == null) {
            return "ERROR: unknown tool '" + call.tool() + "'. Available: " + tools.keySet();
        }
        for (String p : tool.requiredParams()) {
            String v = call.args().get(p);
            if (v == null || v.isBlank()) {
                return "ERROR: missing required parameter '" + p + "' for " + tool.name();
            }
            if (v.length() > 500) {
                return "ERROR: parameter '" + p + "' too long";
            }
        }
        if (tool.risk() == Risk.SIDE_EFFECT && !approver.approve(call)) {
            return "DENIED: a human reviewer rejected " + tool.name() + ". Do not retry; explain to the user.";
        }
        try {
            return tool.function().apply(call.args());
        } catch (RuntimeException e) {
            return "ERROR: " + tool.name() + " failed: " + e.getMessage();
        }
    }
    // endregion
}
