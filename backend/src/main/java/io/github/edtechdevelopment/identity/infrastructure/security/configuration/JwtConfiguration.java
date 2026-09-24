package io.github.edtechdevelopment.identity.infrastructure.security.configuration;

import io.github.edtechdevelopment.identity.application.port.out.security.AccessTokenIssuer;
import io.github.edtechdevelopment.identity.infrastructure.security.token.SpringJwtAccessTokenIssuer;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.crypto.RsaKeyConversionServicePostProcessor;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {

    @Bean
    static BeanFactoryPostProcessor identityRsaKeyConversionServicePostProcessor() {
        return new RsaKeyConversionServicePostProcessor();
    }

    @Bean
    JwtEncoder jwtEncoder(
            @Value("${identity.security.jwt.public-key}") RSAPublicKey publicKey,
            @Value("${identity.security.jwt.private-key}") RSAPrivateKey privateKey
    ) {
        return NimbusJwtEncoder.withKeyPair(publicKey, privateKey).build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${identity.security.jwt.public-key}") RSAPublicKey publicKey
    ) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
                .withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        JwtClaimValidator<Instant> expirationPresenceValidator = new JwtClaimValidator<>(
                JwtClaimNames.EXP,
                Objects::nonNull
        );
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                expirationPresenceValidator,
                timestampValidator
        );
        jwtDecoder.setJwtValidator(validator);

        return jwtDecoder;
    }

    @Bean
    AccessTokenIssuer accessTokenIssuer(JwtEncoder jwtEncoder, IdentityTokenProperties properties) {
        return new SpringJwtAccessTokenIssuer(jwtEncoder, properties);
    }
}
