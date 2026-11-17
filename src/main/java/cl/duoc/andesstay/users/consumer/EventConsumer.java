package cl.duoc.andesstay.users.consumer;

import cl.duoc.andesstay.users.dto.event.UserEvent;
import cl.duoc.andesstay.users.model.ReporteriaEvento;
import cl.duoc.andesstay.users.repository.ReporteriaEventoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consumidor de eventos de Kafka para el subsistema de reporteria en tiempo real y KPI.
 * Escucha el topico configurado (users.events) y persiste cada evento recibido para su consulta analitica.
 */
@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final ReporteriaEventoRepository reporteriaEventoRepository;
    private final ObjectMapper objectMapper;

    public EventConsumer(ReporteriaEventoRepository reporteriaEventoRepository, ObjectMapper objectMapper) {
        this.reporteriaEventoRepository = reporteriaEventoRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${kafka.topic.reporteria:users.events}",
            groupId = "${spring.kafka.consumer.group-id:users-events-group}"
    )
    @Transactional
    public void consume(ConsumerRecord<String, Object> record) {
        if (record == null || record.value() == null) {
            return;
        }

        log.info("Evento consumido desde Kafka: topic={} partition={} offset={} key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        try {
            UserEvent userEvent = null;
            Object value = record.value();

            if (value instanceof UserEvent ue) {
                userEvent = ue;
            } else if (value instanceof String str) {
                userEvent = objectMapper.readValue(str, UserEvent.class);
            } else {
                userEvent = objectMapper.convertValue(value, UserEvent.class);
            }

            if (userEvent == null) {
                log.warn("Payload de Kafka nulo o no convertible a UserEvent en topic={} offset={}",
                        record.topic(), record.offset());
                return;
            }

            String payloadJson = objectMapper.writeValueAsString(userEvent);
            String eventId = userEvent.getEventId() != null ? userEvent.getEventId() : UUID.randomUUID().toString();
            String eventType = userEvent.getEventType() != null ? userEvent.getEventType() : "UNKNOWN";
            UUID userId = userEvent.getUserId() != null ? userEvent.getUserId() : UUID.randomUUID();
            String email = userEvent.getEmail() != null ? userEvent.getEmail() : "no-email@andesstay.cl";

            ReporteriaEvento entidad = new ReporteriaEvento(
                    eventId,
                    eventType,
                    userId,
                    email,
                    payloadJson
            );

            reporteriaEventoRepository.save(entidad);
            log.info("Evento persistido en reporteria: tipo={} userId={}", eventType, userId);

        } catch (Exception e) {
            log.error("Error al procesar y persistir evento de reporteria de Kafka: {}", e.getMessage(), e);
        }
    }
}
