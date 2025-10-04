package com.msvcbilling.config;

import com.mercadopago.MercadoPagoConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigMercadoPago {
    @Value("${mercadopago.access-token}")
    private String accessToken;

    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
        MercadoPagoConfig.setConnectionRequestTimeout(3000);
        MercadoPagoConfig.setSocketTimeout(5000);
    }

}