package com.workflow.segment.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SqlConditionBuilderTest {

    @Test
    void shouldBuildSimpleEquals() {
        var conditions = Map.<String, Object>of(
                "operation", "AND",
                "conditions", List.of(Map.of("field", "age", "operator", ">=", "value", "25")));
        assertThat(SqlConditionBuilder.buildWhereClause(conditions)).isEqualTo("WHERE age >= 25");
    }

    @Test
    void shouldBuildInClause() {
        var conditions = Map.<String, Object>of(
                "operation", "AND",
                "conditions", List.of(Map.of("field", "city", "operator", "IN", "value", List.of("Mumbai", "Delhi"))));
        assertThat(SqlConditionBuilder.buildWhereClause(conditions)).isEqualTo("WHERE city IN ('Mumbai', 'Delhi')");
    }

    @Test
    void shouldBuildBetween() {
        var conditions = Map.<String, Object>of(
                "operation", "AND",
                "conditions", List.of(Map.of("field", "age", "operator", "BETWEEN", "value", List.of("25", "50"))));
        assertThat(SqlConditionBuilder.buildWhereClause(conditions)).isEqualTo("WHERE age BETWEEN 25 AND 50");
    }

    @Test
    void shouldBuildIsNull() {
        var conditions = Map.<String, Object>of(
                "operation", "AND",
                "conditions", List.of(Map.of("field", "email", "operator", "IS NOT NULL")));
        assertThat(SqlConditionBuilder.buildWhereClause(conditions)).isEqualTo("WHERE email IS NOT NULL");
    }

    @Test
    void shouldBuildNestedAndOr() {
        var conditions = Map.<String, Object>of(
                "operation", "AND",
                "conditions", List.of(
                        Map.of("field", "age", "operator", ">=", "value", "25"),
                        Map.of("operation", "OR", "conditions", List.of(
                                Map.of("field", "city", "operator", "=", "value", "Mumbai"),
                                Map.of("field", "city", "operator", "=", "value", "Delhi")))));
        String result = SqlConditionBuilder.buildWhereClause(conditions);
        assertThat(result).isEqualTo("WHERE age >= 25 AND (city = 'Mumbai' OR city = 'Delhi')");
    }

    @Test
    void shouldReturnEmptyForNullConditions() {
        assertThat(SqlConditionBuilder.buildWhereClause(null)).isEqualTo("");
        assertThat(SqlConditionBuilder.buildWhereClause(Map.of())).isEqualTo("");
    }
}
