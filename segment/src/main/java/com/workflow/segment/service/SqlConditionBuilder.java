package com.workflow.segment.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SqlConditionBuilder {

    @SuppressWarnings("unchecked")
    public static String buildWhereClause(Map<String, Object> conditions) {
        if (conditions == null || conditions.isEmpty()) return "";
        return "WHERE " + buildGroup(conditions);
    }

    @SuppressWarnings("unchecked")
    private static String buildGroup(Map<String, Object> group) {
        String operation = (String) group.getOrDefault("operation", "AND");
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) group.get("conditions");
        if (conditions == null || conditions.isEmpty()) return "1=1";

        return conditions.stream()
                .map(cond -> {
                    if (cond.containsKey("conditions")) {
                        return "(" + buildGroup(cond) + ")";
                    } else {
                        return buildLeaf(cond);
                    }
                })
                .collect(Collectors.joining(" " + operation + " "));
    }

    @SuppressWarnings("unchecked")
    private static String buildLeaf(Map<String, Object> cond) {
        String field = (String) cond.get("field");
        String operator = ((String) cond.get("operator")).toUpperCase();
        Object value = cond.get("value");

        return switch (operator) {
            case "IS NULL" -> field + " IS NULL";
            case "IS NOT NULL" -> field + " IS NOT NULL";
            case "IN", "NOT IN" -> {
                List<String> values = (List<String>) value;
                String list = values.stream().map(SqlConditionBuilder::quoteString).collect(Collectors.joining(", "));
                yield field + " " + operator + " (" + list + ")";
            }
            case "BETWEEN" -> {
                List<String> range = (List<String>) value;
                yield field + " BETWEEN " + quoteValue(range.get(0)) + " AND " + quoteValue(range.get(1));
            }
            default -> field + " " + operator + " " + quoteValue((String) value);
        };
    }

    private static String quoteValue(String value) {
        try {
            Double.parseDouble(value);
            return value;
        } catch (NumberFormatException e) {
            return quoteString(value);
        }
    }

    private static String quoteString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
