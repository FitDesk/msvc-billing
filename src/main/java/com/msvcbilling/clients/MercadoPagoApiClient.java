package com.msvcbilling.clients;

import com.msvcbilling.config.ConfigMercadoPago;
import com.msvcbilling.dtos.payment.SubscriptionCancelRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "mercadoPagoApi",
        url = "${mercado-pago.api.base-url}",
        configuration = ConfigMercadoPago.class
)
public interface MercadoPagoApiClient {

    @PutMapping("/preapproval/{id}")
    void cancelSubscription(
            @PathVariable("id") String subscriptionId,
            @RequestBody SubscriptionCancelRequest request
    );

    @PostMapping("/v1/payments/{payment_id}/refunds")
    void createRefund(@PathVariable("payment_id") Long paymentId);

    // Si necesitas hacer una devolución parcial, puedes añadir otro método:
    // @PostMapping("/v1/payments/{payment_id}/refunds")
    // void createPartialRefund(
    //        @PathVariable("payment_id") Long paymentId,
    //        @RequestBody RefundRequest refundRequest
    // );
}