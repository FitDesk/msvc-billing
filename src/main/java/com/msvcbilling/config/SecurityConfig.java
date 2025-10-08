package com.msvcbilling.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;


@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/saludo",
                                "/",
//                                "/payments/**",
//                                "/billing/payments/**",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(
                                "/actuator/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/saludo",
                                "/",
//                                "/payments/**",
//                                "/billing/payments/**",
                                "/error"
                        )
                )
                .build();
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            Map<String, Object> claims = jwt.getClaims();
            Object claim = claims.get("authorities");
            if (claim != null) {
                if (claim instanceof String) {
                    String[] parts = ((String) claim).trim().split("\\s+");
                    for (String p : parts) {
                        if (!p.isBlank())
                            authorities.add(new SimpleGrantedAuthority(p));
                    }
                } else if (claim instanceof Collection<?>) {
                    ((Collection<?>) claim).forEach(o -> {
                        if (o != null)
                            authorities.add(new SimpleGrantedAuthority(o.toString()));
                    });
                } else if (claim instanceof Map<?, ?>) {
                    ((Map<?, ?>) claim).values().forEach(v -> {
                        if (v != null)
                            authorities.add(new SimpleGrantedAuthority(v.toString()));
                    });
                }
            }
            return authorities;
        });
        return converter;
    }
}