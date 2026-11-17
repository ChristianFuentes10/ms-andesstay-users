package cl.duoc.andesstay.users.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * Cliente del SDK de AWS para administrar el User Pool. Solo se crea con
 * cognito.admin.enabled=true. Las credenciales salen de la cadena por defecto del SDK
 * (rol IAM de EC2/ECS, variables AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY, ~/.aws).
 */
@Configuration
@ConditionalOnProperty(name = "cognito.admin.enabled", havingValue = "true")
public class CognitoAdminConfig {

    @Bean(destroyMethod = "close")
    public CognitoIdentityProviderClient cognitoIdentityProviderClient(
            @Value("${cognito.region:us-east-1}") String region) {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .build();
    }
}
