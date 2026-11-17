package cl.duoc.andesstay.users.dto.kafka;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/**
 * Peticion para crear un topico en Kafka desde el microservicio administrador.
 */
public record CreateTopicRequest(
        @NotBlank(message = "El nombre del topico no puede estar vacio")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre del topico contiene caracteres invalidos")
        String name,

        @Min(value = 1, message = "Debe tener al menos 1 particion")
        Integer partitions,

        @Min(value = 1, message = "El factor de replicacion debe ser al menos 1")
        Short replicationFactor,

        Map<String, String> configs
) {
    public CreateTopicRequest {
        if (partitions == null || partitions < 1) {
            partitions = 3;
        }
        if (replicationFactor == null || replicationFactor < 1) {
            replicationFactor = (short) 1;
        }
    }
}
