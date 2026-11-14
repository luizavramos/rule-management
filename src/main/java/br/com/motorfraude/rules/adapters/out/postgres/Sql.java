package br.com.motorfraude.rules.adapters.out.postgres;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Conversões de data e hora entre {@link Instant} e o {@code timestamptz} do Postgres (precisão de microssegundos). */
final class Sql {

    private Sql() {}

    /** O Postgres guarda microssegundos; truncar antes de gravar evita que o valor lido difira do original. */
    static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
