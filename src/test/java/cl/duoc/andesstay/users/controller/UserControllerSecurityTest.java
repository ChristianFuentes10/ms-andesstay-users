package cl.duoc.andesstay.users.controller;

import cl.duoc.andesstay.users.config.CorsConfig;
import cl.duoc.andesstay.users.config.SecurityConfig;
import cl.duoc.andesstay.users.dto.UserResponse;
import cl.duoc.andesstay.users.exception.BusinessRuleException;
import cl.duoc.andesstay.users.exception.GlobalExceptionHandler;
import cl.duoc.andesstay.users.exception.UserAlreadyExistsException;
import cl.duoc.andesstay.users.exception.UserNotFoundException;
import cl.duoc.andesstay.users.model.Role;
import cl.duoc.andesstay.users.security.CognitoJwtAuthenticationConverter;
import cl.duoc.andesstay.users.security.RestAccessDeniedHandler;
import cl.duoc.andesstay.users.security.RestAuthenticationEntryPoint;
import cl.duoc.andesstay.users.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Seguridad y contrato HTTP de /api/users. Mismas reglas que UsersProxySecurityTest del BFF:
 * sin token 401; GET /me cualquier autenticado; listado solo ADMIN; registro autenticado;
 * DELETE solo ADMIN.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, CorsConfig.class, CognitoJwtAuthenticationConverter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class UserControllerSecurityTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    // El nombre "userService" es el que usa @PreAuthorize ("@userService.isOwner(...)").
    @MockBean(name = "userService")
    private UserService userService;

    private static UserResponse sample() {
        return new UserResponse(ID, "juan@test.com", "Juan", "Perez", null, "ACTIVO",
                Set.of(Role.HUESPED), Instant.now(), Instant.now());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject("sub-1")).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    // --- 401 ---

    @Test
    void me_withoutToken_returns401WithErrorBody() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void register_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // --- /me ---

    @Test
    void me_withAnyAuthenticatedUser_returns200() throws Exception {
        when(userService.me("sub-1")).thenReturn(sample());

        mockMvc.perform(get("/api/users/me").with(as("HUESPED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("juan@test.com"));
    }

    @Test
    void me_withoutProfile_returns404() throws Exception {
        when(userService.me("sub-1")).thenThrow(UserNotFoundException.bySub("sub-1"));

        mockMvc.perform(get("/api/users/me").with(as("HUESPED")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    // --- listado ---

    @Test
    void list_withAuditorRole_returns403() throws Exception {
        mockMvc.perform(get("/api/users").with(as("AUDITOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void list_withAdminRole_returns200AndTotalCountHeader() throws Exception {
        when(userService.search(any(), any(), any(), eq(0), eq(50)))
                .thenReturn(new PageImpl<>(List.of(sample()), org.springframework.data.domain.PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/users").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].email").value("juan@test.com"));
    }

    @Test
    void list_withInvalidRoleFilter_returns400() throws Exception {
        mockMvc.perform(get("/api/users?rol=SUPERMAN").with(as("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    // --- GET / PUT por id ---

    @Test
    void getById_withAdmin_returns200() throws Exception {
        when(userService.findById(ID)).thenReturn(sample());

        mockMvc.perform(get("/api/users/" + ID).with(as("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void getById_otherUsersProfile_returns403() throws Exception {
        when(userService.isOwner(ID, "sub-1")).thenReturn(false);

        mockMvc.perform(get("/api/users/" + ID).with(as("HUESPED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_ownProfile_returns200() throws Exception {
        when(userService.isOwner(ID, "sub-1")).thenReturn(true);
        when(userService.findById(ID)).thenReturn(sample());

        mockMvc.perform(get("/api/users/" + ID).with(as("HUESPED")))
                .andExpect(status().isOk());
    }

    @Test
    void getById_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/users/abc").with(as("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_ownProfile_returns200() throws Exception {
        when(userService.isOwner(ID, "sub-1")).thenReturn(true);
        when(userService.update(eq(ID), any(), any())).thenReturn(sample());

        mockMvc.perform(put("/api/users/" + ID).with(as("HUESPED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Juan\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void update_otherUsersProfile_returns403() throws Exception {
        when(userService.isOwner(ID, "sub-1")).thenReturn(false);

        mockMvc.perform(put("/api/users/" + ID).with(as("HUESPED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Juan\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_blankName_returns400() throws Exception {
        mockMvc.perform(put("/api/users/" + ID).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("nombre")));
    }

    // --- registro ---

    @Test
    void register_withValidBody_returns201() throws Exception {
        when(userService.register(any(), any())).thenReturn(sample());

        mockMvc.perform(post("/api/users").with(as("HUESPED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Juan\",\"email\":\"juan@test.com\",\"password\":\"12345678\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void register_withInvalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/users").with(as("HUESPED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Juan\",\"email\":\"no-es-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("email")));
    }

    @Test
    void register_withShortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/users").with(as("HUESPED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Juan\",\"email\":\"juan@test.com\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withUnknownRole_returns400() throws Exception {
        mockMvc.perform(post("/api/users").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Juan\",\"email\":\"juan@test.com\",\"roles\":[\"SUPERMAN\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicatedEmail_returns409() throws Exception {
        when(userService.register(any(), any())).thenThrow(new UserAlreadyExistsException("ya existe"));

        mockMvc.perform(post("/api/users").with(as("HUESPED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Juan\",\"email\":\"juan@test.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("user_already_exists"));
    }

    // --- baja ---

    @Test
    void delete_withAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/" + ID).with(as("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_withoutAdmin_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/" + ID).with(as("HUESPED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_businessRuleViolation_returns409() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessRuleException("ultimo ADMIN"))
                .when(userService).delete(eq(ID), any());

        mockMvc.perform(delete("/api/users/" + ID).with(as("ADMIN")))
                .andExpect(status().isConflict());
    }

    // --- actuator ---

    @Test
    void actuatorHealth_withoutToken_doesNotReturn401() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(401,
                        result.getResponse().getStatus(), "/actuator/health debe ser publico"));
    }

    // --- Endpoints eliminados (viven en reservations) ---

    @Test
    void kafkaAdminEndpoints_haveBeenRemovedFromUsers_return404() throws Exception {
        mockMvc.perform(get("/api/admin/kafka/topics").with(as("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void reporteriaEndpoints_haveBeenRemovedFromUsers_return404() throws Exception {
        mockMvc.perform(get("/api/reporteria/kpi").with(as("ADMIN")))
                .andExpect(status().isNotFound());
    }

    // --- Flujo completo de registro: token autenticado -> POST /api/users -> GET /api/users/me ---

    @Test
    void fullRegistrationFlow_postUsersWithToken_createsHuesped_thenGetMeReturns200() throws Exception {
        String sub = "cognito-new-user-sub-123";
        UUID newUserId = UUID.randomUUID();
        UserResponse createdResponse = new UserResponse(
                newUserId, "nuevo@test.com", "Nuevo", "Huesped", "+56912345678",
                "ACTIVO", Set.of(Role.HUESPED), Instant.now(), Instant.now()
        );

        when(userService.register(any(), eq(new cl.duoc.andesstay.users.service.CurrentUser(sub, false))))
                .thenReturn(createdResponse);
        when(userService.me(sub)).thenReturn(createdResponse);

        // Paso 1: Post-login en Cognito, la aplicacion invoca POST /api/users con el token para registrar perfil local
        mockMvc.perform(post("/api/users")
                        .with(jwt().jwt(j -> j.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_HUESPED")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nuevo\",\"apellido\":\"Huesped\",\"email\":\"nuevo@test.com\",\"telefono\":\"+56912345678\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(newUserId.toString()))
                .andExpect(jsonPath("$.email").value("nuevo@test.com"))
                .andExpect(jsonPath("$.roles[0]").value("HUESPED"));

        // Paso 2: La aplicacion consulta su perfil con GET /api/users/me usando el mismo token
        mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(j -> j.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_HUESPED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(newUserId.toString()))
                .andExpect(jsonPath("$.email").value("nuevo@test.com"))
                .andExpect(jsonPath("$.roles[0]").value("HUESPED"));
    }
}
