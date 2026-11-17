package cl.duoc.andesstay.users.dto.reporteria;

import java.time.Instant;
import java.util.UUID;

public record ReporteriaEventoResponse(
        UUID id,
        String eventId,
        String eventType,
        UUID userId,
        String email,
        String payload,
        Instant createdAt
) {
}
