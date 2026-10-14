package dev.aieng.p15;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A first-pass EU AI Act risk triage for an AI use case, as a structured checklist an architect can run in a
 * design review. It encodes the Act's broad tiers; it is NOT legal advice - borderline cases go to legal/compliance.
 */
public final class AiActTriage {

    public enum Tier { PROHIBITED, HIGH_RISK, LIMITED_RISK_TRANSPARENCY, MINIMAL_RISK }

    /** Selected characteristics of the use case (simplified from Article 5, Annex III and Article 50). */
    public enum Trait {
        SOCIAL_SCORING, MANIPULATIVE_TECHNIQUES, EMOTION_RECOGNITION_AT_WORK, UNTARGETED_FACE_SCRAPING,
        EMPLOYMENT_DECISIONS, CREDITWORTHINESS, ESSENTIAL_SERVICES_ACCESS, EDUCATION_ASSESSMENT, CRITICAL_INFRASTRUCTURE,
        INTERACTS_WITH_PEOPLE, GENERATES_SYNTHETIC_CONTENT,
        INTERNAL_PRODUCTIVITY
    }

    public record Result(Tier tier, List<String> obligations) {
    }

    private static final Set<Trait> PROHIBITED = EnumSet.of(Trait.SOCIAL_SCORING, Trait.MANIPULATIVE_TECHNIQUES,
            Trait.EMOTION_RECOGNITION_AT_WORK, Trait.UNTARGETED_FACE_SCRAPING);
    private static final Set<Trait> HIGH = EnumSet.of(Trait.EMPLOYMENT_DECISIONS, Trait.CREDITWORTHINESS,
            Trait.ESSENTIAL_SERVICES_ACCESS, Trait.EDUCATION_ASSESSMENT, Trait.CRITICAL_INFRASTRUCTURE);

    private AiActTriage() {
    }

    // region triage
    public static Result triage(Set<Trait> traits) {
        if (traits.stream().anyMatch(PROHIBITED::contains)) {
            return new Result(Tier.PROHIBITED, List.of("Do not build or deploy this use case in the EU."));
        }
        List<String> obligations = new ArrayList<>();
        Tier tier = Tier.MINIMAL_RISK;
        if (traits.stream().anyMatch(HIGH::contains)) {
            tier = Tier.HIGH_RISK;
            obligations.addAll(List.of(
                    "Risk management system across the lifecycle",
                    "Data governance: relevant, representative, documented training and test data",
                    "Technical documentation and automatic event logging (record-keeping)",
                    "Transparency and instructions for use for deployers",
                    "Human oversight measures",
                    "Accuracy, robustness and cybersecurity requirements",
                    "Conformity assessment, registration and post-market monitoring"));
        }
        if (traits.contains(Trait.INTERACTS_WITH_PEOPLE)) {
            obligations.add("Tell people they are interacting with an AI system");
            tier = tier == Tier.MINIMAL_RISK ? Tier.LIMITED_RISK_TRANSPARENCY : tier;
        }
        if (traits.contains(Trait.GENERATES_SYNTHETIC_CONTENT)) {
            obligations.add("Mark AI-generated or manipulated content as such (machine-readable where feasible)");
            tier = tier == Tier.MINIMAL_RISK ? Tier.LIMITED_RISK_TRANSPARENCY : tier;
        }
        obligations.add("AI literacy for staff operating the system");
        return new Result(tier, List.copyOf(obligations));
    }
    // endregion
}
