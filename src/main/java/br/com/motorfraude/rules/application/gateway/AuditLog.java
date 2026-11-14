package br.com.motorfraude.rules.application.gateway;

import br.com.motorfraude.rules.domain.AuditEntry;
import java.util.List;

public interface AuditLog {

    void append(AuditEntry entry);

    List<AuditEntry> recent(int limit);
}
