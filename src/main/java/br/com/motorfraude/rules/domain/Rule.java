package br.com.motorfraude.rules.domain;

import java.time.Instant;

public record Rule(String ruleId, String createdBy, Instant createdAt) {}
