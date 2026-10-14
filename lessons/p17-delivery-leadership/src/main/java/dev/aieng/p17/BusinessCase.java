package dev.aieng.p17;

/**
 * ROI of an AI feature as a small, explicit model: value from time saved, scaled by adoption and by the share of
 * outputs that are good enough to use, minus run and build costs. Every input is a stated assumption that a
 * stakeholder can challenge - which is the point.
 */
public final class BusinessCase {

    public record Assumptions(
            long tasksPerMonth,          // e.g. tickets handled
            double minutesSavedPerTask,  // when the AI output is used
            double adoption,             // share of tasks where people use the feature
            double usableRate,           // share of AI outputs good enough to use (from evals / pilot)
            double reviewMinutesPerTask, // time spent checking AI output - a real cost
            double loadedCostPerHour,    // fully loaded labour cost
            double aiCostPerTask,        // tokens + infrastructure per task
            double monthlyPlatformCost,  // run cost independent of volume (support, monitoring, licences)
            double buildCost) {          // one-off
    }

    public record Result(double monthlyBenefit, double monthlyCost, double monthlyNet, double paybackMonths) {
    }

    private BusinessCase() {
    }

    // region roi
    public static Result evaluate(Assumptions a) {
        double usedTasks = a.tasksPerMonth() * a.adoption();
        double hoursSaved = usedTasks * (a.usableRate() * a.minutesSavedPerTask() - a.reviewMinutesPerTask()) / 60;
        double benefit = hoursSaved * a.loadedCostPerHour();
        double cost = usedTasks * a.aiCostPerTask() + a.monthlyPlatformCost();
        double net = benefit - cost;
        return new Result(benefit, cost, net, net <= 0 ? Double.POSITIVE_INFINITY : a.buildCost() / net);
    }
    // endregion

    /** Smallest usable rate that breaks even - the quality bar the eval suite must prove before go-live. */
    public static double breakEvenUsableRate(Assumptions a) {
        double lo = 0;
        double hi = 1;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            Assumptions probe = new Assumptions(a.tasksPerMonth(), a.minutesSavedPerTask(), a.adoption(), mid,
                    a.reviewMinutesPerTask(), a.loadedCostPerHour(), a.aiCostPerTask(), a.monthlyPlatformCost(), a.buildCost());
            if (evaluate(probe).monthlyNet() >= 0) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }

    public static void main(String[] args) {
        Assumptions support = new Assumptions(180_000, 4, 0.7, 0.75, 0.5, 45, 0.04, 6_000, 250_000);
        Result r = evaluate(support);
        System.out.printf("benefit €%,.0f/month, cost €%,.0f/month, net €%,.0f/month, payback %.1f months%n",
                r.monthlyBenefit(), r.monthlyCost(), r.monthlyNet(), r.paybackMonths());
        System.out.printf("break-even requires >= %.0f%% usable outputs%n", 100 * breakEvenUsableRate(support));
    }
}
