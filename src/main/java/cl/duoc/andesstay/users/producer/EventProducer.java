package cl.duoc.andesstay.users.producer;

import cl.duoc.andesstay.users.dto.event.UserEvent;
import cl.duoc.andesstay.users.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Productor de eventos de usuario para Apache Kafka.
 * Publica al topico de reporteria usando el ID del usuario como clave (partition key).
 */
@Component
public class EventProducer {

    private static final Logger log = LoggerFactory.getLogger(EventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String reporteriaTopic;

    public EventProducer(@org.springframework.beans.factory.annotation.Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
                         @Value("${kafka.topic.reporteria:users.events}") String reporteriaTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.reporteriaTopic = reporteriaTopic;
    }

    public void publishUserEvent(User user, String eventType) {
        if (kafkaTemplate == null || user == null || user.getId() == null) {
            return;
        }

        UserEvent event = new UserEvent(
                UUID.randomUUID().toString(),
                eventType,
                user.getId(),
                user.getEmail(),
                user.getNombre(),
                user.getApellido(),
                user.getRoles(),
                user.getEstado(),
                Instant.now(),
                "ms-andesstay-users"
        );

        String partitionKey = user.getId().toString();

        try {
            kafkaTemplate.send(reporteriaTopic, partitionKey, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Fallo al emitir evento Kafka tipo={} para usuarioId={}: {}",
                                    eventType, partitionKey, ex.getMessage());
                        } else {
                            log.info("Evento Kafka publicado exitosamente: tipo={} usuarioId={} topic={} partition={} offset={}",
                                    eventType, partitionKey, reporteriaTopic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Error no bloqueante al enviar evento a Kafka (topic={}): {}", reporteriaTopic, e.getMessage());
        }
    }
}
