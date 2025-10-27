package com.msvcbilling.config;

import com.mercadopago.MercadoPagoConfig;
import feign.RequestTemplate;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import feign.RequestInterceptor;


@Configuration
@Slf4j
public class ConfigMercadoPago {
    @Value("${mercadopago.access-token}")
    private String accessToken;

    @PostConstruct
    public void init() {
        log.info("Access Token de mercado pago {}", accessToken);
        MercadoPagoConfig.setAccessToken(accessToken);
        MercadoPagoConfig.setConnectionRequestTimeout(5000);
        MercadoPagoConfig.setSocketTimeout(10000);
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                template.header("Authorization", "Bearer " + accessToken);
                template.header("Content-Type", "application/json");
            }
        };
    }
}