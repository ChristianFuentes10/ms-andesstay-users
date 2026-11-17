package cl.duoc.andesstay.users.controller;

import cl.duoc.andesstay.users.dto.RegisterUserRequest;
import cl.duoc.andesstay.users.dto.UpdateUserRequest;
import cl.duoc.andesstay.users.dto.UserResponse;
import cl.duoc.andesstay.users.model.Role;
import cl.duoc.andesstay.users.model.UserStatus;
import cl.duoc.andesstay.users.service.CurrentUser;
import cl.duoc.andesstay.users.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * API de usuarios. El BFF expone estas mismas rutas bajo /api/users.
 * La autorizacion gruesa esta en SecurityConfig; la fina (dueno del perfil) en @PreAuthorize.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Registro de usuario (Flujo autenticado):
     *  1. El cliente se registra (SignUp) en AWS Cognito y confirma el codigo enviado a su correo.
     *  2. Inicia sesion en Cognito y obtiene su Access Token (JWT con claim 'sub').
     *  3. Envia POST /api/users con el token en el header Authorization: Bearer.
     *  4. El microservicio crea el perfil en PostgreSQL vinculado a dicho 'sub', asignando
     *     por defecto el rol HUESPED (auto-registro seguro sin escalada de privilegios).
     *     Si quien invoca tiene rol ADMIN, puede crear perfiles con roles personalizados.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterUserRequest request, Authentication authentication) {
        return userService.register(request, CurrentUser.from(authentication));
    }

    /** Perfil del usuario autenticado. */
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return userService.me(authentication.getName());
    }

    /** Listado paginado con filtros opcionales; total en el header X-Total-Count. Solo ADMIN. */
    @GetMapping
    public ResponseEntity<List<UserResponse>> findAll(
            @RequestParam(required = false) Role rol,
            @RequestParam(required = false) UserStatus estado,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<UserResponse> result = userService.search(rol, estado, q, page, size);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .body(result.getContent());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @userService.isOwner(#id, authentication.name)")
    public UserResponse findById(@PathVariable UUID id) {
        return userService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @userService.isOwner(#id, authentication.name)")
    public UserResponse update(@PathVariable UUID id,
                               @Valid @RequestBody UpdateUserRequest request,
                               Authentication authentication) {
        return userService.update(id, request, CurrentUser.from(authentication));
    }

    /** Baja logica. Solo ADMIN. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication authentication) {
        userService.delete(id, CurrentUser.from(authentication));
    }
}
