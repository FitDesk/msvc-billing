package com.msvcbilling.config;

import com.mercadopago.client.cardtoken.CardTokenClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.paymentmethod.PaymentMethodClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MercadoPagoClientConfig {

    @Bean
    public PaymentClient paymentClient() {
        return new PaymentClient();
    }

    @Bean
    public CardTokenClient cardTokenClient() {
        return new CardTokenClient();
    }

    @Bean
    public PaymentMethodClient paymentMethodClient() {
        return new PaymentMethodClient();
    }
}