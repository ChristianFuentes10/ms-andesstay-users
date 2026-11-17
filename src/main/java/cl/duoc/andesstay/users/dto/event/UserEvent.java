package cl.duoc.andesstay.users.dto.event;

import cl.duoc.andesstay.users.model.Role;
import cl.duoc.andesstay.users.model.UserStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Evento de dominio emitido a Kafka cuando ocurre una accion sobre un usuario.
 * Cumple con el patron Event-Driven Architecture (EDA) y la rubrica de la Entrega 3.
 */
public class UserEvent implements Serializable {

    private String eventId;
    private String eventType;
    private UUID userId;
    private String email;
    private String nombre;
    private String apellido;
    private Set<Role> roles;
    private UserStatus estado;
    private Instant timestamp;
    private String source;

    public UserEvent() {
    }

    public UserEvent(String eventId, String eventType, UUID userId, String email, String nombre,
                     String apellido, Set<Role> roles, UserStatus estado, Instant timestamp, String source) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.userId = userId;
        this.email = email;
        this.nombre = nombre;
        this.apellido = apellido;
        this.roles = roles;
        this.estado = estado;
        this.timestamp = timestamp;
        this.source = source;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public UserStatus getEstado() {
        return estado;
    }

    public void setEstado(UserStatus estado) {
        this.estado = estado;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public String toString() {
        return "UserEvent{" +
                "eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", userId=" + userId +
                ", email='" + email + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
