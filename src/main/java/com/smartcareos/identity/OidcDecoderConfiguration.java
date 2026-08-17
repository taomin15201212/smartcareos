package com.smartcareos.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;

@Configuration
public class OidcDecoderConfiguration {
    @Bean
    @ConditionalOnProperty(name="smartcareos.security.auth-mode",havingValue="oidc")
    JwtDecoder oidcJwtDecoder(org.springframework.core.env.Environment environment) {
        return JwtDecoders.fromIssuerLocation(environment.getRequiredProperty(
                "smartcareos.security.oidc.issuer-uri"));
    }
}
