package cl.duoc.andesstay.users.consumer;

import cl.duoc.andesstay.users.dto.event.UserEvent;
import cl.duoc.andesstay.users.model.ReporteriaEvento;
import cl.duoc.andesstay.users.model.Role;
import cl.duoc.andesstay.users.model.UserStatus;
import cl.duoc.andesstay.users.repository.ReporteriaEventoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock
    private ReporteriaEventoRepository reporteriaEventoRepository;

    private ObjectMapper objectMapper;
    private EventConsumer eventConsumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        eventConsumer = new EventConsumer(reporteriaEventoRepository, objectMapper);
    }

    @Test
    void consume_withUserEventInstance_persistsReporteriaEvento() {
        UUID userId = UUID.randomUUID();
        UserEvent userEvent = new UserEvent(
                "evt-100",
                "USER_REGISTERED",
                userId,
                "maria@andesstay.cl",
                "Maria",
                "Gonzalez",
                Set.of(Role.HUESPED),
                UserStatus.ACTIVO,
                Instant.now(),
                "ms-andesstay-users"
        );

        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "users.events", 0, 10L, userId.toString(), userEvent
        );

        eventConsumer.consume(record);

        ArgumentCaptor<ReporteriaEvento> captor = ArgumentCaptor.forClass(ReporteriaEvento.class);
        verify(reporteriaEventoRepository).save(captor.capture());

        ReporteriaEvento saved = captor.getValue();
        assertEquals("evt-100", saved.getEventId());
        assertEquals("USER_REGISTERED", saved.getEventType());
        assertEquals(userId, saved.getUserId());
        assertEquals("maria@andesstay.cl", saved.getEmail());
        assertNotNull(saved.getPayload());
        assertTrue(saved.getPayload().contains("Maria"));
    }

    @Test
    void consume_withJsonString_persistsReporteriaEvento() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEvent userEvent = new UserEvent(
                "evt-101",
                "USER_UPDATED",
                userId,
                "carlos@andesstay.cl",
                "Carlos",
                "Silva",
                Set.of(Role.HUESPED),
                UserStatus.ACTIVO,
                Instant.now(),
                "ms-andesstay-users"
        );
        String json = objectMapper.writeValueAsString(userEvent);

        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "users.events", 0, 11L, userId.toString(), json
        );

        eventConsumer.consume(record);

        ArgumentCaptor<ReporteriaEvento> captor = ArgumentCaptor.forClass(ReporteriaEvento.class);
        verify(reporteriaEventoRepository).save(captor.capture());

        ReporteriaEvento saved = captor.getValue();
        assertEquals("evt-101", saved.getEventId());
        assertEquals("USER_UPDATED", saved.getEventType());
        assertEquals("carlos@andesstay.cl", saved.getEmail());
    }

    @Test
    void consume_whenMalformedRecord_doesNotThrow() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "users.events", 0, 12L, "key", "not-a-valid-json"
        );

        assertDoesNotThrow(() -> eventConsumer.consume(record));
        verify(reporteriaEventoRepository, never()).save(any());
    }
}

