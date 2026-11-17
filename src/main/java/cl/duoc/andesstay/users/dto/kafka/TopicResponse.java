package cl.duoc.andesstay.users.dto.kafka;

import java.util.Map;

/**
 * Informacion detallada de un topico de Kafka.
 */
public record TopicResponse(
        String name,
        int partitions,
        short replicationFactor,
        Map<String, String> configs
) {
}
