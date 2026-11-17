package cl.duoc.andesstay.users.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Perfil de usuario de AndesStay.
 *
 * Las credenciales NO se guardan aqui: las administra AWS Cognito (IDaaS). Este
 * microservicio guarda el perfil de negocio y los roles, y los mantiene
 * sincronizados con los grupos de Cognito.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** Claim "sub" del token de Cognito; vincula el perfil con la identidad. */
    @Column(name = "cognito_sub", unique = true, length = 100)
    private String cognitoSub;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 100)
    private String apellido;

    @Column(length = 30)
    private String telefono;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private UserStatus estado = UserStatus.ACTIVO;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User() {
    }

    public User(String cognitoSub, String email, String nombre, String apellido, String telefono, Set<Role> roles) {
        this.cognitoSub = cognitoSub;
        this.email = email;
        this.nombre = nombre;
        this.apellido = apellido;
        this.telefono = telefono;
        setRoles(roles);
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return estado == UserStatus.ACTIVO;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCognitoSub() { return cognitoSub; }
    public void setCognitoSub(String cognitoSub) { this.cognitoSub = cognitoSub; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public UserStatus getEstado() { return estado; }
    public void setEstado(UserStatus estado) { this.estado = estado; }

    public Set<Role> getRoles() { return roles; }

    /** Reemplaza los roles (copia defensiva; nunca deja la coleccion en null). */
    public void setRoles(Set<Role> newRoles) {
        Set<Role> copy = EnumSet.noneOf(Role.class);
        if (newRoles != null) {
            copy.addAll(newRoles);
        }
        this.roles = copy;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
