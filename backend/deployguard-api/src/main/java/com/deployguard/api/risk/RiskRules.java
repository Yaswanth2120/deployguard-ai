package com.deployguard.api.risk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic risk rules. Must match shared/risk-rules.json, which is the single source of
 * truth shared with the Python AI service; RiskRulesContractTest enforces it in both directions.
 */
public final class RiskRules {

    public static final String VERSION = "deterministic-v2";
    public static final Set<String> ERROR_LOG_LEVELS = Set.of("ERROR", "FATAL");
    public static final Set<String> PRODUCTION_ENVIRONMENTS = Set.of("production", "prod");
    public static final int FAILED_CI_RUN = 30;
    public static final int FAILED_TESTS = 20;
    public static final int ERROR_LOGS = 30;
    public static final int HOTFIX_BRANCH = 10;
    public static final int PRODUCTION_ENVIRONMENT = 10;
    public static final int MAX_SCORE = 100;
    public static final int LOW_MAX = 30;
    public static final int MEDIUM_MAX = 70;

    public record Factor(String name, int contribution, String detail) {}

    private RiskRules() {
    }

    public static List<Factor> factors(
            String branch,
            String environment,
            Collection<String> ciStatuses,
            int failedTests,
            Collection<String> logLevels) {
        List<Factor> factors = new ArrayList<>();
        if (ciStatuses.stream().anyMatch(status -> "FAILED".equalsIgnoreCase(status))) {
            factors.add(new Factor("failed_ci_run", FAILED_CI_RUN, "At least one CI run failed for this commit."));
        }
        if (failedTests > 0) {
            factors.add(new Factor("failed_tests", FAILED_TESTS, failedTests + " failed test(s) reported for this commit."));
        }
        if (logLevels.stream().anyMatch(RiskRules::isErrorLevel)) {
            factors.add(new Factor("error_logs", ERROR_LOGS, "ERROR or FATAL logs are linked to this change."));
        }
        if (branch != null && branch.toLowerCase(Locale.ROOT).contains("hotfix")) {
            factors.add(new Factor("hotfix_branch", HOTFIX_BRANCH, "Branch is marked as a hotfix."));
        }
        if (environment != null && PRODUCTION_ENVIRONMENTS.contains(environment.toLowerCase(Locale.ROOT))) {
            factors.add(new Factor("production_environment", PRODUCTION_ENVIRONMENT, "Targets production."));
        }
        return factors;
    }

    public static int score(List<Factor> factors) {
        return Math.min(MAX_SCORE, factors.stream().mapToInt(Factor::contribution).sum());
    }

    public static String level(int score) {
        if (score <= LOW_MAX) {
            return "LOW";
        }
        if (score <= MEDIUM_MAX) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    public static boolean isErrorLevel(String level) {
        return level != null && ERROR_LOG_LEVELS.contains(level.toUpperCase(Locale.ROOT));
    }
}
