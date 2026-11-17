package cl.duoc.andesstay.users.consumer;

import cl.duoc.andesstay.users.dto.event.UserEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de eventos de dominio de usuario desde Apache Kafka.
 * Escucha el topico dedicado 'users.events' con su propio group-id sin colisionar
 * con los consumidores de reservations ni de la DLT de reporteria.
 */
@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final ObjectMapper objectMapper;

    public EventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${kafka.topic.users-events:users.events}",
            groupId = "${spring.kafka.consumer.group-id:ms-andesstay-users-events-group}"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        log.info("Evento de usuario consumido desde Kafka: topic={} partition={} offset={} key={}",
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

            log.info("Evento procesado con exito: tipo={} usuarioId={} email={}",
                    userEvent.getEventType(), userEvent.getUserId(), userEvent.getEmail());

        } catch (Exception e) {
            log.error("Error al procesar evento de usuario desde Kafka: {}", e.getMessage(), e);
        }
    }
}
