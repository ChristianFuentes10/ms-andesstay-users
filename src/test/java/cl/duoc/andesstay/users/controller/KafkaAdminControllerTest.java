package cl.duoc.andesstay.users.controller;

import cl.duoc.andesstay.users.config.CorsConfig;
import cl.duoc.andesstay.users.config.SecurityConfig;
import cl.duoc.andesstay.users.dto.kafka.CreateTopicRequest;
import cl.duoc.andesstay.users.dto.kafka.TopicResponse;
import cl.duoc.andesstay.users.dto.kafka.UpdateTopicRequest;
import cl.duoc.andesstay.users.exception.GlobalExceptionHandler;
import cl.duoc.andesstay.users.security.CognitoJwtAuthenticationConverter;
import cl.duoc.andesstay.users.security.RestAccessDeniedHandler;
import cl.duoc.andesstay.users.security.RestAuthenticationEntryPoint;
import cl.duoc.andesstay.users.service.KafkaAdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KafkaAdminController.class)
@Import({SecurityConfig.class, CorsConfig.class, CognitoJwtAuthenticationConverter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class KafkaAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private KafkaAdminService kafkaAdminService;

    @Test
    void listTopics_sinToken_retorna401() throws Exception {
        mockMvc.perform(get("/api/admin/kafka/topics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listTopics_rolHuesped_retorna403() throws Exception {
        mockMvc.perform(get("/api/admin/kafka/topics")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_HUESPED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTopics_rolAdmin_retorna200() throws Exception {
        TopicResponse topic = new TopicResponse("reporteria-eventos", 3, (short) 1, Map.of());
        when(kafkaAdminService.listTopics()).thenReturn(List.of(topic));

        mockMvc.perform(get("/api/admin/kafka/topics")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("reporteria-eventos"))
                .andExpect(jsonPath("$[0].partitions").value(3));
    }

    @Test
    void createTopic_rolAdmin_retorna201() throws Exception {
        CreateTopicRequest request = new CreateTopicRequest("nuevo-topico", 3, (short) 1, Map.of());
        TopicResponse created = new TopicResponse("nuevo-topico", 3, (short) 1, Map.of());
        when(kafkaAdminService.createTopic(any())).thenReturn(created);

        mockMvc.perform(post("/api/admin/kafka/topics")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("nuevo-topico"));
    }

    @Test
    void updateTopic_rolAdmin_retorna200() throws Exception {
        UpdateTopicRequest request = new UpdateTopicRequest(5, Map.of());
        TopicResponse updated = new TopicResponse("topico-modificado", 5, (short) 1, Map.of());
        when(kafkaAdminService.updateTopic(eq("topico-modificado"), any())).thenReturn(updated);

        mockMvc.perform(put("/api/admin/kafka/topics/topico-modificado")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partitions").value(5));
    }

    @Test
    void deleteTopic_rolAdmin_retorna204() throws Exception {
        doNothing().when(kafkaAdminService).deleteTopic("topico-a-borrar");

        mockMvc.perform(delete("/api/admin/kafka/topics/topico-a-borrar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
    }
}
