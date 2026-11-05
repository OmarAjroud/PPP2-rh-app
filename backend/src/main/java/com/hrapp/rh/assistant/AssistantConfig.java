package com.hrapp.rh.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfig {

    /** Client de l'API Claude, créé seulement si une clé est configurée (ANTHROPIC_API_KEY). */
    @Bean(destroyMethod = "close")
    @ConditionalOnExpression("'${app.assistant.claude.api-key:}'.trim() != ''")
    public AnthropicClient anthropicClient(AssistantProperties properties) {
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.claude().apiKey())
                .build();
    }
}
