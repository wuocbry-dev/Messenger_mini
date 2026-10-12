package com.team503.messenger.call.config;

import com.team503.messenger.call.service.AgoraTokenService;
import com.team503.messenger.call.service.DefaultAgoraTokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgoraProperties.class)
public class AgoraConfig {

    @Bean
    AgoraTokenService agoraTokenService(AgoraProperties properties) {
        return new DefaultAgoraTokenService(properties);
    }
}
