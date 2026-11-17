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
import cl.duoc.andesstay.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    private static final CurrentUser GUEST = new CurrentUser("sub-guest", false);
    private static final CurrentUser ADMIN = new CurrentUser("sub-admin", true);

    @Mock
    private UserRepository userRepository;

    @Mock
    private IdentityProviderClient identityProvider;

    @Mock
    private cl.duoc.andesstay.users.producer.EventProducer eventProducer;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(identityProvider.findEmailBySub(anyString())).thenReturn(Optional.empty());
    }

    private static RegisterUserRequest registerRequest(Set<Role> roles) {
        return new RegisterUserRequest("Juan", "Perez", "  Juan@Test.com ", "+56 9 1234 5678", "12345678", roles);
    }

    private static User user(UUID id, String sub, String email, UserStatus estado, Role... roles) {
        User u = new User(sub, email, "Nombre", null, null,
                roles.length == 0 ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(List.of(roles)));
        u.setId(id);
        u.setEstado(estado);
        return u;
    }

    // ------------------------------------------------------------ registro

    @Test
    void register_selfRegistration_forcesHuespedRoleAndNormalizesEmail() {
        UserResponse result = userService.register(registerRequest(Set.of(Role.ADMIN)), GUEST);

        assertEquals("juan@test.com", result.email());
        assertEquals(Set.of(Role.HUESPED), result.roles(), "el auto-registro nunca puede elegir roles");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("sub-guest", captor.getValue().getCognitoSub());
        verify(identityProvider).syncGroups("juan@test.com", Set.of(Role.HUESPED));
        verify(identityProvider, never()).createUser(any(), any(), any(), any());
    }

    @Test
    void register_duplicatedEmail_throwsConflict() {
        when(userRepository.existsByEmailIgnoreCase("juan@test.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> userService.register(registerRequest(null), GUEST));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_selfRegistration_whenSubAlreadyHasProfile_throwsConflict() {
        when(userRepository.existsByCognitoSub("sub-guest")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> userService.register(registerRequest(null), GUEST));
    }

    @Test
    void register_selfRegistration_emailDifferentFromCognito_isRejected() {
        when(identityProvider.findEmailBySub("sub-guest")).thenReturn(Optional.of("otro@test.com"));

        assertThrows(IllegalArgumentException.class, () -> userService.register(registerRequest(null), GUEST));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_byAdmin_createsIdentityAndAssignsRequestedRoles() {
        when(identityProvider.createUser(eq("juan@test.com"), eq("Juan"), eq("Perez"), eq("12345678")))
                .thenReturn("new-sub");

        UserResponse result = userService.register(
                registerRequest(Set.of(Role.RECEPCIONISTA, Role.AUDITOR)), ADMIN);

        assertEquals(Set.of(Role.RECEPCIONISTA, Role.AUDITOR), result.roles());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertEquals("new-sub", captor.getValue().getCognitoSub());
        verify(identityProvider).syncGroups("juan@test.com", Set.of(Role.RECEPCIONISTA, Role.AUDITOR));
    }

    @Test
    void register_byAdmin_withoutRoles_defaultsToHuesped() {
        UserResponse result = userService.register(registerRequest(null), ADMIN);

        assertEquals(Set.of(Role.HUESPED), result.roles());
    }

    @Test
    void register_byAdmin_whenLocalSaveFails_compensatesIdentity() {
        when(identityProvider.createUser(any(), any(), any(), any())).thenReturn("new-sub");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> userService.register(registerRequest(null), ADMIN));
        verify(identityProvider).deleteUser("juan@test.com");
    }

    // ------------------------------------------------------------ consulta

    @Test
    void me_existingProfile_returnsIt() {
        when(userRepository.findByCognitoSub("sub-guest"))
                .thenReturn(Optional.of(user(UUID.randomUUID(), "sub-guest", "a@a.com", UserStatus.ACTIVO, Role.HUESPED)));

        assertEquals("a@a.com", userService.me("sub-guest").email());
    }

    @Test
    void me_withoutProfile_throwsNotFound() {
        when(userRepository.findByCognitoSub("sub-x")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.me("sub-x"));
    }

    @Test
    void me_linksPrecreatedProfileByVerifiedEmail() {
        User pre = user(UUID.randomUUID(), null, "boss@test.com", UserStatus.ACTIVO, Role.ADMIN);
        when(userRepository.findByCognitoSub("sub-boss")).thenReturn(Optional.empty());
        when(identityProvider.findEmailBySub("sub-boss")).thenReturn(Optional.of("boss@test.com"));
        when(userRepository.findByEmailIgnoreCase("boss@test.com")).thenReturn(Optional.of(pre));

        userService.me("sub-boss");

        assertEquals("sub-boss", pre.getCognitoSub());
        verify(userRepository).save(pre);
    }

    @Test
    void me_doesNotStealProfileAlreadyLinkedToAnotherSub() {
        User other = user(UUID.randomUUID(), "sub-other", "boss@test.com", UserStatus.ACTIVO, Role.ADMIN);
        when(userRepository.findByCognitoSub("sub-evil")).thenReturn(Optional.empty());
        when(identityProvider.findEmailBySub("sub-evil")).thenReturn(Optional.of("boss@test.com"));
        when(userRepository.findByEmailIgnoreCase("boss@test.com")).thenReturn(Optional.of(other));

        assertThrows(UserNotFoundException.class, () -> userService.me("sub-evil"));
        assertEquals("sub-other", other.getCognitoSub());
    }

    @Test
    void findById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.findById(id));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_clampsPageSize() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        Page<UserResponse> page = userService.search(null, null, "  ", -5, 100_000);

        assertTrue(page.isEmpty());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(UserService.MAX_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void isOwner_matchesSub() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id))
                .thenReturn(Optional.of(user(id, "sub-guest", "a@a.com", UserStatus.ACTIVO, Role.HUESPED)));

        assertTrue(userService.isOwner(id, "sub-guest"));
        assertFalse(userService.isOwner(id, "sub-other"));
        assertFalse(userService.isOwner(UUID.randomUUID(), "sub-guest"));
    }

    // ------------------------------------------------------------ actualizacion

    @Test
    void update_byOwner_changesOnlyProfileFields() {
        UUID id = UUID.randomUUID();
        User existing = user(id, "sub-guest", "a@a.com", UserStatus.ACTIVO, Role.HUESPED);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));

        UserResponse result = userService.update(id,
                new UpdateUserRequest(" Ana ", " Soto ", "", null, null), GUEST);

        assertEquals("Ana", result.nombre());
        assertEquals("Soto", result.apellido());
        assertNull(result.telefono());
        assertEquals(Set.of(Role.HUESPED), result.roles());
        verify(identityProvider, never()).syncGroups(any(), any());
    }

    @Test
    void update_nonAdminTryingToChangeRoles_isForbidden() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id))
                .thenReturn(Optional.of(user(id, "sub-guest", "a@a.com", UserStatus.ACTIVO, Role.HUESPED)));

        assertThrows(AccessDeniedException.class, () -> userService.update(id,
                new UpdateUserRequest("Ana", null, null, Set.of(Role.ADMIN), null), GUEST));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_byAdmin_changesRolesAndSyncsCognito() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id))
                .thenReturn(Optional.of(user(id, "sub-guest", "a@a.com", UserStatus.ACTIVO, Role.HUESPED)));

        UserResponse result = userService.update(id,
                new UpdateUserRequest("Ana", null, null, Set.of(Role.RECEPCIONISTA), null), ADMIN);

        assertEquals(Set.of(Role.RECEPCIONISTA), result.roles());
        verify(identityProvider).syncGroups("a@a.com", Set.of(Role.RECEPCIONISTA));
    }

    @Test
    void update_emptyRoles_isRejected() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id))
                .thenReturn(Optional.of(user(id, "sub-guest", "a@a.com", UserStatus.ACTIVO, Role.HUESPED)));

        assertThrows(IllegalArgumentException.class, () -> userService.update(id,
                new UpdateUserRequest("Ana", null, null, Set.of(), null), ADMIN));
    }

    @Test
    void update_removingAdminRoleFromLastActiveAdmin_isRejected() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id))
                .thenReturn(Optional.of(user(id, "sub-other-admin", "b@a.com", UserStatus.ACTIVO, Role.ADMIN)));
        when(userRepository.countByRoleAndEstado(Role.ADMIN, UserStatus.ACTIVO)).thenReturn(1L);

        assertThrows(BusinessRuleException.class, () -> userService.update(id,
                new UpdateUserRequest("B", null, null, Set.of(Role.HUESPED), null), ADMIN));
    }

    @Test
    void update_adminCannotRemoveOwnAdminRole() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id))
                .thenReturn(Optional.of(user(id, "sub-admin", "b@a.com", UserStatus.ACTIVO, Role.ADMIN)));
        when(userRepository.countByRoleAndEstado(Role.ADMIN, UserStatus.ACTIVO)).thenReturn(3L);

        assertThrows(BusinessRuleException.class, () -> userService.update(id,
                new UpdateUserRequest("B", null, null, Set.of(Role.HUESPED), null), ADMIN));
    }

    @Test
    void update_deactivatingUser_disablesLoginInCognito() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id))
                .thenReturn(Optional.of(user(id, "sub-guest", "a@a.com", UserStatus.ACTIVO, Role.HUESPED)));

        UserResponse result = userService.update(id,
                new UpdateUserRequest("Ana", null, null, null, UserStatus.INACTIVO), ADMIN);

        assertEquals("INACTIVO", result.estado());
        verify(identityProvider).setEnabled("a@a.com", false);
    }

    // ------------------------------------------------------------ baja

    @Test
    void delete_marksInactiveAndDisablesCognito() {
        UUID id = UUID.randomUUID();
        User existing = user(id, "sub-guest", "a@a.com", UserStatus.ACTIVO, Role.HUESPED);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));

        userService.delete(id, ADMIN);

        assertEquals(UserStatus.INACTIVO, existing.getEstado());
        verify(userRepository, never()).delete(any(User.class));
        verify(identityProvider).setEnabled("a@a.com", false);
    }

    @Test
    void delete_alreadyInactive_isIdempotent() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id))
                .thenReturn(Optional.of(user(id, "sub-guest", "a@a.com", UserStatus.INACTIVO, Role.HUESPED)));

        userService.delete(id, ADMIN);

        verify(userRepository, never()).saveAndFlush(any());
        verify(identityProvider, never()).setEnabled(any(), anyBoolean());
    }

    @Test
    void delete_ownAccount_isRejected() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id))
                .thenReturn(Optional.of(user(id, "sub-admin", "b@a.com", UserStatus.ACTIVO, Role.ADMIN)));

        assertThrows(BusinessRuleException.class, () -> userService.delete(id, ADMIN));
    }

    @Test
    void delete_lastActiveAdmin_isRejected() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id))
                .thenReturn(Optional.of(user(id, "sub-other-admin", "b@a.com", UserStatus.ACTIVO, Role.ADMIN)));
        when(userRepository.countByRoleAndEstado(Role.ADMIN, UserStatus.ACTIVO)).thenReturn(1L);

        assertThrows(BusinessRuleException.class, () -> userService.delete(id, ADMIN));
    }

    @Test
    void delete_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.delete(id, ADMIN));
    }
}
