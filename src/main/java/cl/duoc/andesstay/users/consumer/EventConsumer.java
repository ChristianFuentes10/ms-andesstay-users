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

/**
 * Consumidor de eventos de Kafka para el subsistema de reporteria en tiempo real y KPI.
 * Escucha el topico configurado y persiste cada evento recibido para su consulta analitica.
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
            topics = "${kafka.topic.reporteria:reporteria-eventos}",
            groupId = "${spring.kafka.consumer.group-id:ms-andesstay-users-group}"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        log.info("Evento consumido desde Kafka: topic={} partition={} offset={} key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        try {
            UserEvent userEvent;
            Object value = record.value();

            if (value instanceof UserEvent ue) {
                userEvent = ue;
            } else if (value instanceof String str) {
                userEvent = objectMapper.readValue(str, UserEvent.class);
            } else {
                userEvent = objectMapper.convertValue(value, UserEvent.class);
            }

            String payloadJson = objectMapper.writeValueAsString(userEvent);

            ReporteriaEvento entidad = new ReporteriaEvento(
                    userEvent.getEventId(),
                    userEvent.getEventType(),
                    userEvent.getUserId(),
                    userEvent.getEmail(),
                    payloadJson
            );

            reporteriaEventoRepository.save(entidad);
            log.info("Evento persistido en reporteria: tipo={} userId={}",
                    userEvent.getEventType(), userEvent.getUserId());

        } catch (Exception e) {
            log.error("Error al procesar y persistir evento de reporteria de Kafka: {}", e.getMessage(), e);
        }
    }
}
