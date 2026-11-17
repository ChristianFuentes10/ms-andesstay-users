package cl.duoc.andesstay.users.controller;

import cl.duoc.andesstay.users.dto.kafka.CreateTopicRequest;
import cl.duoc.andesstay.users.dto.kafka.TopicResponse;
import cl.duoc.andesstay.users.dto.kafka.UpdateTopicRequest;
import cl.duoc.andesstay.users.service.KafkaAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST para la administracion de topicos y configuraciones de Apache Kafka.
 * Requerimiento expreso de la rubrica (Entrega 3):
 * "El microservicio administrador de Kafka debe permitir la creacion, modificacion y eliminacion de topicos y configuraciones."
 */
@RestController
@RequestMapping("/api/admin/kafka/topics")
@Tag(name = "Kafka Admin", description = "Gestion dinamica de topicos y configuraciones de Kafka (Solo ADMIN)")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class KafkaAdminController {

    private final KafkaAdminService kafkaAdminService;

    public KafkaAdminController(KafkaAdminService kafkaAdminService) {
        this.kafkaAdminService = kafkaAdminService;
    }

    @GetMapping
    @Operation(summary = "Listar topicos", description = "Obtiene la lista de todos los topicos de Kafka y sus particiones")
    public List<TopicResponse> listTopics() {
        return kafkaAdminService.listTopics();
    }

    @GetMapping("/{name}")
    @Operation(summary = "Detalle de topico", description = "Consulta particiones, factor de replicacion y configuraciones del topico")
    public TopicResponse getTopic(@PathVariable String name) {
        return kafkaAdminService.getTopicDetails(name);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear topico", description = "Crea un nuevo topico con las particiones y configuraciones especificadas")
    public TopicResponse createTopic(@Valid @RequestBody CreateTopicRequest request) {
        return kafkaAdminService.createTopic(request);
    }

    @PutMapping("/{name}")
    @Operation(summary = "Modificar topico", description = "Aumenta particiones o altera configuraciones del topico en tiempo real")
    public TopicResponse updateTopic(@PathVariable String name, @Valid @RequestBody UpdateTopicRequest request) {
        return kafkaAdminService.updateTopic(name, request);
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "Eliminar topico", description = "Elimina un topico existente de Kafka")
    public ResponseEntity<Void> deleteTopic(@PathVariable String name) {
        kafkaAdminService.deleteTopic(name);
        return ResponseEntity.noContent().build();
    }
}
