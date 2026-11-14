package br.com.motorfraude.rules.application.gateway;

import br.com.motorfraude.rules.domain.AuditEntry;
import java.util.List;

/** Trilha de auditoria append-only: só acrescenta e lê, nunca altera. */
public interface AuditLog {

    void append(AuditEntry entry);

    List<AuditEntry> recent(int limit);
}
