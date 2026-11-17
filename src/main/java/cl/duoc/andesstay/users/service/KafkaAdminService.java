package cl.duoc.andesstay.users.service;

import cl.duoc.andesstay.users.dto.kafka.CreateTopicRequest;
import cl.duoc.andesstay.users.dto.kafka.TopicResponse;
import cl.duoc.andesstay.users.dto.kafka.UpdateTopicRequest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.config.ConfigResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de administracion de Kafka mediante AdminClient.
 * Cumple con el requerimiento de la rubrica:
 * "El microservicio administrador de Kafka debe permitir la creacion, modificacion y eliminacion de topicos y configuraciones."
 */
@Service
public class KafkaAdminService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);
    private static final int TIMEOUT_SECONDS = 10;

    private final AdminClient adminClient;

    public KafkaAdminService(AdminClient adminClient) {
        this.adminClient = adminClient;
    }

    /**
     * Lista todos los topicos existentes (excluyendo internos) con su cantidad de particiones.
     */
    public List<TopicResponse> listTopics() {
        try {
            Set<String> topicNames = adminClient.listTopics().names().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (topicNames.isEmpty()) {
                return Collections.emptyList();
            }

            Map<String, TopicDescription> descriptions = adminClient.describeTopics(topicNames)
                    .allTopicNames().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            List<TopicResponse> result = new ArrayList<>();
            for (Map.Entry<String, TopicDescription> entry : descriptions.entrySet()) {
                String name = entry.getKey();
                TopicDescription desc = entry.getValue();
                int partitions = desc.partitions().size();
                short replicationFactor = desc.partitions().isEmpty() ? 1 :
                        (short) desc.partitions().get(0).replicas().size();

                result.add(new TopicResponse(name, partitions, replicationFactor, Collections.emptyMap()));
            }
            return result;
        } catch (Exception e) {
            log.error("Error al listar topicos de Kafka: {}", e.getMessage());
            throw new RuntimeException("No se pudieron consultar los topicos de Kafka: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene el detalle completo de un topico, incluyendo sus configuraciones activas.
     */
    public TopicResponse getTopicDetails(String topicName) {
        try {
            TopicDescription desc = adminClient.describeTopics(Collections.singletonList(topicName))
                    .allTopicNames().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).get(topicName);

            if (desc == null) {
                throw new IllegalArgumentException("El topico " + topicName + " no existe");
            }

            int partitions = desc.partitions().size();
            short replicationFactor = desc.partitions().isEmpty() ? 1 :
                    (short) desc.partitions().get(0).replicas().size();

            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
            Map<ConfigResource, Config> configsMap = adminClient.describeConfigs(Collections.singletonList(resource))
                    .all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Map<String, String> configs = new HashMap<>();
            Config config = configsMap.get(resource);
            if (config != null) {
                for (ConfigEntry entry : config.entries()) {
                    if (!entry.isDefault()) {
                        configs.put(entry.name(), entry.value());
                    }
                }
            }

            return new TopicResponse(topicName, partitions, replicationFactor, configs);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error al obtener detalle del topico {}: {}", topicName, e.getMessage());
            throw new RuntimeException("Error al consultar el topico " + topicName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Crea un nuevo topico en Kafka con particiones, factor de replicacion y configuraciones opcionales.
     */
    public TopicResponse createTopic(CreateTopicRequest request) {
        try {
            NewTopic newTopic = new NewTopic(request.name(), request.partitions(), request.replicationFactor());
            if (request.configs() != null && !request.configs().isEmpty()) {
                newTopic.configs(request.configs());
            }

            adminClient.createTopics(Collections.singletonList(newTopic)).all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Topico creado exitosamente: name={} partitions={}", request.name(), request.partitions());

            return getTopicDetails(request.name());
        } catch (Exception e) {
            log.error("Error al crear topico {}: {}", request.name(), e.getMessage());
            throw new RuntimeException("Error al crear el topico en Kafka: " + e.getMessage(), e);
        }
    }

    /**
     * Modifica las particiones o configuraciones de un topico existente.
     */
    public TopicResponse updateTopic(String topicName, UpdateTopicRequest request) {
        try {
            // Modificar particiones si fue solicitado
            if (request.partitions() != null) {
                TopicDescription desc = adminClient.describeTopics(Collections.singletonList(topicName))
                        .allTopicNames().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).get(topicName);

                int currentPartitions = desc.partitions().size();
                if (request.partitions() > currentPartitions) {
                    Map<String, NewPartitions> newPartitions = Map.of(
                            topicName, NewPartitions.increaseTo(request.partitions())
                    );
                    adminClient.createPartitions(newPartitions).all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    log.info("Particiones aumentadas para {}: de {} a {}", topicName, currentPartitions, request.partitions());
                } else if (request.partitions() < currentPartitions) {
                    throw new IllegalArgumentException("Kafka no permite reducir particiones (actual: "
                            + currentPartitions + ", solicitada: " + request.partitions() + ")");
                }
            }

            // Modificar configuraciones si fueron solicitadas
            if (request.configs() != null && !request.configs().isEmpty()) {
                ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
                Collection<AlterConfigOp> ops = new ArrayList<>();
                for (Map.Entry<String, String> entry : request.configs().entrySet()) {
                    ops.add(new AlterConfigOp(
                            new ConfigEntry(entry.getKey(), entry.getValue()),
                            AlterConfigOp.OpType.SET
                    ));
                }
                adminClient.incrementalAlterConfigs(Map.of(resource, ops)).all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                log.info("Configuraciones actualizadas para topico {}: {}", topicName, request.configs());
            }

            return getTopicDetails(topicName);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error al modificar topico {}: {}", topicName, e.getMessage());
            throw new RuntimeException("Error al modificar el topico " + topicName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Elimina un topico existente en Kafka.
     */
    public void deleteTopic(String topicName) {
        try {
            adminClient.deleteTopics(Collections.singletonList(topicName)).all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Topico {} eliminado exitosamente", topicName);
        } catch (Exception e) {
            log.error("Error al eliminar topico {}: {}", topicName, e.getMessage());
            throw new RuntimeException("Error al eliminar el topico " + topicName + ": " + e.getMessage(), e);
        }
    }
}
