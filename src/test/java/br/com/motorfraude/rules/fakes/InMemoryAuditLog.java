package br.com.motorfraude.rules.fakes;

import br.com.motorfraude.rules.application.gateway.AuditLog;
import br.com.motorfraude.rules.domain.AuditEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryAuditLog implements AuditLog {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void append(AuditEntry entry) {
        entries.add(entry);
    }

    @Override
    public List<AuditEntry> recent(int limit) {
        var copy = new ArrayList<>(entries);
        Collections.reverse(copy);
        return copy.stream().limit(limit).toList();
    }

    public List<AuditEntry> all() {
        return List.copyOf(entries);
    }
}
