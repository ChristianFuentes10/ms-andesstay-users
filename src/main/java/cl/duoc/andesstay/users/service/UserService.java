package cl.duoc.andesstay.users.service;

import cl.duoc.andesstay.users.dto.RegisterUserRequest;
import cl.duoc.andesstay.users.dto.UpdateUserRequest;
import cl.duoc.andesstay.users.dto.UserResponse;
import cl.duoc.andesstay.users.exception.BusinessRuleException;
import cl.duoc.andesstay.users.exception.UserAlreadyExistsException;
import cl.duoc.andesstay.users.exception.UserNotFoundException;
import cl.duoc.andesstay.users.idp.IdentityProviderClient;
import cl.duoc.andesstay.users.model.Role;
import cl.duoc.andesstay.users.model.User;
import cl.duoc.andesstay.users.model.UserStatus;
import cl.duoc.andesstay.users.producer.EventProducer;
import cl.duoc.andesstay.users.repository.UserRepository;
import cl.duoc.andesstay.users.repository.UserSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Logica de negocio de usuarios: registro, consulta, actualizacion, roles y baja logica.
 *
 * Reglas principales:
 *  - Credenciales en Cognito; aqui solo perfil y roles (sincronizados con los grupos de Cognito).
 *  - Auto-registro: siempre rol HUESPED (el cliente nunca elige sus roles).
 *  - Solo ADMIN asigna roles / cambia estado.
 *  - Nunca se deja el sistema sin un ADMIN activo ni un ADMIN se auto-degrada/auto-elimina.
 *  - La baja es logica (INACTIVO) y deshabilita el login en Cognito.
 *  - Publica eventos a Kafka (EventProducer) para reporteria y auditoria.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    static final int MAX_PAGE_SIZE = 200;

    private final UserRepository userRepository;
    private final IdentityProviderClient identityProvider;
    private final EventProducer eventProducer;

    public UserService(UserRepository userRepository, IdentityProviderClient identityProvider) {
        this(userRepository, identityProvider, null);
    }

    public UserService(UserRepository userRepository,
                       IdentityProviderClient identityProvider,
                       EventProducer eventProducer) {
        this.userRepository = userRepository;
        this.identityProvider = identityProvider;
        this.eventProducer = eventProducer;
    }

    // ------------------------------------------------------------------ registro

    @Transactional
    public UserResponse register(RegisterUserRequest request, CurrentUser caller) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new UserAlreadyExistsException("Ya existe un usuario registrado con el email " + email);
        }

        User user = caller.admin()
                ? registerByAdmin(request, email)
                : registerSelf(request, email, caller);

        log.info("Usuario registrado id={} roles={} (por {})", user.getId(), user.getRoles(),
                caller.admin() ? "ADMIN" : "auto-registro");

        emitUserEvent(user, "USER_REGISTERED");
        return UserResponse.fromEntity(user);
    }

    /** El usuario autenticado crea SU propio perfil: se vincula al "sub" del token con rol HUESPED. */
    private User registerSelf(RegisterUserRequest request, String email, CurrentUser caller) {
        if (userRepository.existsByCognitoSub(caller.sub())) {
            throw new UserAlreadyExistsException("El usuario autenticado ya tiene un perfil registrado");
        }

        // El access token no trae email: si hay Cognito, se usa su email verificado como fuente de verdad.
        Optional<String> verifiedEmail = identityProvider.findEmailBySub(caller.sub());
        if (verifiedEmail.isPresent() && !verifiedEmail.get().equalsIgnoreCase(email)) {
            throw new IllegalArgumentException("El email no coincide con el de la cuenta autenticada");
        }

        Set<Role> roles = EnumSet.of(Role.HUESPED);
        User user = new User(caller.sub(), email, request.nombre().trim(),
                trimToNull(request.apellido()), trimToNull(request.telefono()), roles);
        user = userRepository.save(user);

        identityProvider.syncGroups(email, roles);
        return user;
    }

    /** Un ADMIN da de alta a otro usuario con los roles indicados (por defecto HUESPED). */
    private User registerByAdmin(RegisterUserRequest request, String email) {
        Set<Role> roles = request.roles() == null || request.roles().isEmpty()
                ? EnumSet.of(Role.HUESPED)
                : EnumSet.copyOf(request.roles());

        String sub = identityProvider.createUser(email, request.nombre().trim(),
                trimToNull(request.apellido()), request.password());
        try {
            User user = new User(sub, email, request.nombre().trim(),
                    trimToNull(request.apellido()), trimToNull(request.telefono()), roles);
            user = userRepository.saveAndFlush(user);
            identityProvider.syncGroups(email, roles);
            return user;
        } catch (RuntimeException e) {
            // Compensacion: no dejar una identidad huerfana en Cognito si falla el guardado local.
            identityProvider.deleteUser(email);
            throw e;
        }
    }

    // ------------------------------------------------------------------ consultas

    /**
     * Perfil del usuario autenticado. Si un ADMIN lo pre-creo (sin "sub") y Cognito esta
     * habilitado, se vincula en el primer acceso por email verificado.
     */
    @Transactional
    public UserResponse me(String sub) {
        Optional<User> bySub = userRepository.findByCognitoSub(sub);
        if (bySub.isPresent()) {
            return UserResponse.fromEntity(bySub.get());
        }
        User linked = identityProvider.findEmailBySub(sub)
                .flatMap(userRepository::findByEmailIgnoreCase)
                .filter(u -> u.getCognitoSub() == null)
                .map(u -> {
                    u.setCognitoSub(sub);
                    return userRepository.save(u);
                })
                .orElseThrow(() -> UserNotFoundException.bySub(sub));
        log.info("Perfil {} vinculado al sub de Cognito en el primer acceso", linked.getId());
        return UserResponse.fromEntity(linked);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return UserResponse.fromEntity(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> search(Role role, UserStatus estado, String q, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String filter = q == null || q.isBlank() ? null : q.trim();
        return userRepository.findAll(UserSpecifications.filter(role, estado, filter),
                        PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(UserResponse::fromEntity);
    }

    /** Usado desde @PreAuthorize: true si el perfil {id} pertenece al usuario autenticado. */
    @Transactional(readOnly = true)
    public boolean isOwner(UUID id, String sub) {
        return userRepository.findById(id)
                .map(u -> u.getCognitoSub() != null && u.getCognitoSub().equals(sub))
                .orElse(false);
    }

    // ------------------------------------------------------------------ actualizacion

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, CurrentUser caller) {
        User user = getOrThrow(id);
        boolean self = user.getCognitoSub() != null && user.getCognitoSub().equals(caller.sub());

        if (!caller.admin() && (request.roles() != null || request.estado() != null)) {
            throw new AccessDeniedException("Solo un ADMIN puede modificar roles o estado");
        }

        user.setNombre(request.nombre().trim());
        user.setApellido(trimToNull(request.apellido()));
        user.setTelefono(trimToNull(request.telefono()));

        boolean rolesChanged = false;
        boolean statusChanged = false;

        if (caller.admin() && request.roles() != null) {
            if (request.roles().isEmpty()) {
                throw new IllegalArgumentException("El usuario debe tener al menos un rol");
            }
            Set<Role> newRoles = EnumSet.copyOf(request.roles());
            if (!newRoles.equals(user.getRoles())) {
                if (user.hasRole(Role.ADMIN) && !newRoles.contains(Role.ADMIN)) {
                    guardAdminRemoval(user, self, "quitarle el rol ADMIN");
                }
                user.setRoles(newRoles);
                rolesChanged = true;
            }
        }

        if (caller.admin() && request.estado() != null && request.estado() != user.getEstado()) {
            if (request.estado() == UserStatus.INACTIVO) {
                guardAdminRemoval(user, self, "desactivarlo");
            }
            user.setEstado(request.estado());
            statusChanged = true;
        }

        User saved = userRepository.saveAndFlush(user);

        if (rolesChanged) {
            identityProvider.syncGroups(saved.getEmail(), saved.getRoles());
            emitUserEvent(saved, "USER_ROLE_CHANGED");
        } else if (statusChanged) {
            emitUserEvent(saved, saved.isActive() ? "USER_ACTIVATED" : "USER_DEACTIVATED");
        } else {
            emitUserEvent(saved, "USER_UPDATED");
        }

        if (statusChanged) {
            identityProvider.setEnabled(saved.getEmail(), saved.isActive());
        }
        return UserResponse.fromEntity(saved);
    }

    // ------------------------------------------------------------------ baja

    /** Baja logica: pasa a INACTIVO y se deshabilita en Cognito. Idempotente. */
    @Transactional
    public void delete(UUID id, CurrentUser caller) {
        User user = getOrThrow(id);
        if (!user.isActive()) {
            return;
        }
        boolean self = user.getCognitoSub() != null && user.getCognitoSub().equals(caller.sub());
        if (self) {
            throw new BusinessRuleException("Un administrador no puede eliminar su propia cuenta");
        }
        guardAdminRemoval(user, false, "eliminarlo");

        user.setEstado(UserStatus.INACTIVO);
        userRepository.saveAndFlush(user);
        identityProvider.setEnabled(user.getEmail(), false);
        emitUserEvent(user, "USER_DEACTIVATED");
        log.info("Usuario {} dado de baja (logica)", user.getId());
    }

    // ------------------------------------------------------------------ helpers

    private void emitUserEvent(User user, String eventType) {
        if (eventProducer != null && user != null && user.getId() != null) {
            try {
                eventProducer.publishUserEvent(user, eventType);
            } catch (Exception e) {
                log.warn("No se pudo emitir evento Kafka tipo={}: {}", eventType, e.getMessage());
            }
        }
    }

    /**
     * Impide dejar el sistema sin administradores activos y que un ADMIN se quite
     * a si mismo el acceso. Solo aplica si el usuario objetivo es un ADMIN activo.
     */
    private void guardAdminRemoval(User target, boolean self, String action) {
        if (!target.hasRole(Role.ADMIN) || !target.isActive()) {
            return;
        }
        if (self) {
            throw new BusinessRuleException("Un administrador no puede " + action + " a si mismo");
        }
        if (userRepository.countByRoleAndEstado(Role.ADMIN, UserStatus.ACTIVO) <= 1) {
            throw new BusinessRuleException("No se puede " + action + ": es el ultimo ADMIN activo del sistema");
        }
    }

    private User getOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> UserNotFoundException.byId(id));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
