package br.com.motorfraude.rules.domain;

import java.time.Instant;
import java.util.Map;

public record AuditEntry(String actor, String action, String entity, Map<String, Object> before,
        Map<String, Object> after, Instant at) {}
