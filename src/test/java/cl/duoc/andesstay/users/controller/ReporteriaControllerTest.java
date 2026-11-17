package cl.duoc.andesstay.users.controller;

import cl.duoc.andesstay.users.config.CorsConfig;
import cl.duoc.andesstay.users.config.SecurityConfig;
import cl.duoc.andesstay.users.dto.reporteria.KpiResponse;
import cl.duoc.andesstay.users.exception.GlobalExceptionHandler;
import cl.duoc.andesstay.users.model.Role;
import cl.duoc.andesstay.users.security.CognitoJwtAuthenticationConverter;
import cl.duoc.andesstay.users.security.RestAccessDeniedHandler;
import cl.duoc.andesstay.users.security.RestAuthenticationEntryPoint;
import cl.duoc.andesstay.users.service.ReporteriaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReporteriaController.class)
@Import({SecurityConfig.class, CorsConfig.class, CognitoJwtAuthenticationConverter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class ReporteriaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ReporteriaService reporteriaService;

    @Test
    void getKpi_sinToken_retorna401() throws Exception {
        mockMvc.perform(get("/api/reporteria/kpi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getKpi_rolHuesped_retorna403() throws Exception {
        mockMvc.perform(get("/api/reporteria/kpi")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_HUESPED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getKpi_rolAdmin_retorna200() throws Exception {
        KpiResponse kpi = new KpiResponse(
                10L, 8L, 2L,
                Map.of(Role.ADMIN, 2L, Role.HUESPED, 8L),
                25L,
                Map.of("USER_REGISTERED", 10L),
                List.of()
        );
        when(reporteriaService.getKpiSummary()).thenReturn(kpi);

        mockMvc.perform(get("/api/reporteria/kpi")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsuarios").value(10))
                .andExpect(jsonPath("$.usuariosActivos").value(8))
                .andExpect(jsonPath("$.totalEventosKafkaProcesados").value(25));
    }
}
