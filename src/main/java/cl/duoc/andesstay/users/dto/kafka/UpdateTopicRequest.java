package cl.duoc.andesstay.users.dto.kafka;

import jakarta.validation.constraints.Min;

import java.util.Map;

/**
 * Peticion para modificar particiones o configuraciones de un topico en Kafka.
 */
public record UpdateTopicRequest(
        @Min(value = 1, message = "El nuevo numero total de particiones debe ser mayor a 0")
        Integer partitions,

        Map<String, String> configs
) {
}
