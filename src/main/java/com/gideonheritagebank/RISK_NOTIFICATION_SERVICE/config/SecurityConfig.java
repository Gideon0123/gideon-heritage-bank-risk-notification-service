package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.security.SecurityApiErrorHandler;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Supports both:
     * 1. Authorization: Bearer <access-token>
     * 2. ACCESS_TOKEN cookie
     * The Authorization header takes priority.
     */
    @Bean
    public BearerTokenResolver bearerTokenResolver(
            @Value("${identity.security.cookies.access-name:ACCESS_TOKEN}")
            String accessCookieName
    ) {
        DefaultBearerTokenResolver headerTokenResolver = new DefaultBearerTokenResolver();

        return request -> {

            String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

            // Standard Bearer authentication takes priority.
            if (authorizationHeader != null
                    && authorizationHeader.regionMatches(
                    true,
                    0,
                    "Bearer",
                    0,
                    6
            )) {

                return headerTokenResolver.resolve(request);
            }

            // Fall back to the ACCESS_TOKEN cookie.
            Cookie[] cookies = request.getCookies();

            if (cookies == null) {
                return null;
            }

            for (Cookie cookie : cookies) {

                if (accessCookieName.equals(cookie.getName())
                        && cookie.getValue() != null
                        && !cookie.getValue().isBlank()) {

                    return cookie.getValue();
                }
            }

            return null;
        };
    }

    /**
     * Validates JWT signatures using the Identity Service JWKS endpoint.
     * These properties intentionally match the API Gateway configuration.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${bank.security.jwt.jwk-set-uri}")
            String jwkSetUri,

            @Value("${bank.security.jwt.issuer-uri}")
            String issuerUri,

            @Value("${bank.security.jwt.audience}")
            String audience
    ) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> issuerAndTimestampValidator =
                JwtValidators.createDefaultWithIssuer(issuerUri);

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {

            List<String> audiences = jwt.getAudience();

            if (audiences != null && audiences.contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }

            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "The token audience is not trusted by this API.",
                    null
            );

            return OAuth2TokenValidatorResult.failure(error);
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        issuerAndTimestampValidator,
                        audienceValidator
                )
        );

        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            Collection<GrantedAuthority> authorities = new ArrayList<>(scopeConverter.convert(jwt));

            Object rolesClaim = jwt.getClaims().get("roles");

            if (rolesClaim instanceof Collection<?> roles) {

                for (Object role : roles) {

                    if (role instanceof String roleName && !roleName.isBlank()) {

                        String authority = roleName.startsWith("ROLE_")
                                ? roleName : "ROLE_" + roleName;

                        authorities.add(
                                new SimpleGrantedAuthority(authority)
                        );
                    }
                }
            }

            return authorities;
        });

        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BearerTokenResolver bearerTokenResolver,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            SecurityApiErrorHandler securityApiErrorHandler
    ) throws Exception {

        http
                // The Gateway handles CSRF protection for external
                // browser requests. This service is accessed through it.
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(authorize -> authorize

                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()

                        .anyRequest().authenticated()
                )

                .oauth2ResourceServer(oauth2 -> oauth2

                        .bearerTokenResolver(bearerTokenResolver)

                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                )

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityApiErrorHandler)
                        .accessDeniedHandler(securityApiErrorHandler)
                );

        return http.build();
    }
}
