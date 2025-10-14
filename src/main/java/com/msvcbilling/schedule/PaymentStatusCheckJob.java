package com.msvcbilling.schedule;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import com.msvcbilling.entities.PaymentEntity;
import com.msvcbilling.repository.PaymentRepository;
import com.msvcbilling.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentStatusCheckJob {

    private final PaymentRepository paymentRepository;
    private final PaymentClient paymentClient;
    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 300000) //-> 5 min
    public void checkPendingPayment() {
        log.info("Verificando pagos pendientes");
        List<PaymentEntity> pendingPayments = paymentRepository.findByStatus("in_process");
        log.info("Pagos pendientes : {}", pendingPayments.size());
        for (PaymentEntity localPayment : pendingPayments) {
            try {
                if (localPayment.getPaymentId() == null)
                    continue;
                Payment mpPayment = paymentClient.get(localPayment.getPaymentId());

                if (mpPayment != null && !mpPayment.getStatus().equals(localPayment.getStatus())) {
                    log.info("Estado actualizado: {} -> {}", localPayment.getStatus(), mpPayment.getStatus());

                    paymentService.updatePaymentFromMpPayment(mpPayment);
                }
            } catch (
                    Exception e) {
                log.error("Error al consultar pago: {} {}", localPayment.getPaymentId(), e.getMessage());
            }
        }
    }
}
