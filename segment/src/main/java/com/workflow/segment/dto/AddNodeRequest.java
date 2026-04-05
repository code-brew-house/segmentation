package com.workflow.segment.dto;

import java.util.Map;

public record AddNodeRequest(String type, Map<String, Object> config) {}
