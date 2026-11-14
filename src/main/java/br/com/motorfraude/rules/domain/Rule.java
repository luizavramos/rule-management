package br.com.motorfraude.rules.domain;

import java.time.Instant;

/** Identidade de uma regra; o conteúdo vive nas versões ({@link RuleVersion}). */
public record Rule(String ruleId, String createdBy, Instant createdAt) {}
