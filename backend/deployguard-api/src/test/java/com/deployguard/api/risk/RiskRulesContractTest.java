package com.deployguard.api.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Keeps the Java rules identical to shared/risk-rules.json (also enforced on the Python side). */
class RiskRulesContractTest {

    private static final Path RULES_FILE = Path.of("..", "..", "shared", "risk-rules.json");

    private static JsonNode rules() throws IOException {
        return new ObjectMapper().readTree(RULES_FILE.toFile());
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    @Test
    void constantsMatchSharedRules() throws IOException {
        JsonNode rules = rules();
        JsonNode weights = rules.get("weights");
        assertThat(RiskRules.VERSION).isEqualTo(rules.get("version").asText());
        assertThat(RiskRules.FAILED_CI_RUN).isEqualTo(weights.get("failed_ci_run").asInt());
        assertThat(RiskRules.FAILED_TESTS).isEqualTo(weights.get("failed_tests").asInt());
        assertThat(RiskRules.ERROR_LOGS).isEqualTo(weights.get("error_logs").asInt());
        assertThat(RiskRules.HOTFIX_BRANCH).isEqualTo(weights.get("hotfix_branch").asInt());
        assertThat(RiskRules.PRODUCTION_ENVIRONMENT).isEqualTo(weights.get("production_environment").asInt());
        assertThat(RiskRules.MAX_SCORE).isEqualTo(rules.get("maxScore").asInt());
        assertThat(RiskRules.LOW_MAX).isEqualTo(rules.get("thresholds").get("lowMax").asInt());
        assertThat(RiskRules.MEDIUM_MAX).isEqualTo(rules.get("thresholds").get("mediumMax").asInt());
        assertThat(RiskRules.ERROR_LOG_LEVELS).isEqualTo(Set.copyOf(strings(rules.get("errorLogLevels"))));
        assertThat(RiskRules.PRODUCTION_ENVIRONMENTS).isEqualTo(new HashSet<>(strings(rules.get("productionEnvironments"))));
    }

    @Test
    void sharedTestVectorsPass() throws IOException {
        for (JsonNode vector : rules().get("testVectors")) {
            List<RiskRules.Factor> factors = RiskRules.factors(
                    vector.get("branch").asText(),
                    vector.get("environment").asText(),
                    strings(vector.get("ciStatuses")),
                    vector.get("failedTests").asInt(),
                    strings(vector.get("logLevels")));
            int score = RiskRules.score(factors);
            assertThat(score).as(vector.get("name").asText()).isEqualTo(vector.get("expectedScore").asInt());
            assertThat(RiskRules.level(score)).as(vector.get("name").asText()).isEqualTo(vector.get("expectedLevel").asText());
        }
    }
}
