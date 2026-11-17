package cl.duoc.andesstay.users.producer;

import cl.duoc.andesstay.users.dto.event.UserEvent;
import cl.duoc.andesstay.users.model.Role;
import cl.duoc.andesstay.users.model.User;
import cl.duoc.andesstay.users.model.UserStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private EventProducer eventProducer;
    private static final String TOPIC = "users.events";

    @BeforeEach
    void setUp() {
        eventProducer = new EventProducer(kafkaTemplate, TOPIC);
    }

    @Test
    void publishUserEvent_withValidUser_publishesToUsersEventsTopic() {
        UUID userId = UUID.randomUUID();
        User user = new User("sub-123", "test@andesstay.cl", "Test", "User", "+56911112222",
                EnumSet.of(Role.HUESPED));
        user.setId(userId);
        user.setEstado(UserStatus.ACTIVO);

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(TOPIC, userId.toString(), null);
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(TOPIC, 0), 0, 0, 0, 0, 0);
        SendResult<String, Object> sendResult = new SendResult<>(producerRecord, metadata);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(eq(TOPIC), eq(userId.toString()), any(UserEvent.class)))
                .thenReturn(future);

        eventProducer.publishUserEvent(user, "USER_REGISTERED");

        ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(userId.toString()), captor.capture());

        UserEvent captured = captor.getValue();
        assertEquals(userId, captured.getUserId());
        assertEquals("test@andesstay.cl", captured.getEmail());
        assertEquals("USER_REGISTERED", captured.getEventType());
        assertEquals(Set.of(Role.HUESPED), captured.getRoles());
        assertEquals("ms-andesstay-users", captured.getSource());
    }

    @Test
    void publishUserEvent_whenKafkaTemplateNull_doesNotThrow() {
        EventProducer producerWithoutKafka = new EventProducer(null, TOPIC);
        User user = new User("sub-123", "test@andesstay.cl", "Test", "User", null, EnumSet.of(Role.HUESPED));
        user.setId(UUID.randomUUID());

        assertDoesNotThrow(() -> producerWithoutKafka.publishUserEvent(user, "USER_REGISTERED"));
    }

    @Test
    void publishUserEvent_whenUserNull_doesNotPublish() {
        eventProducer.publishUserEvent(null, "USER_REGISTERED");
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }
}

