package com.msvcbilling.utils;

import com.msvcbilling.entities.PlanEntity;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Slf4j
public class PaymentMethods {

    public static void validateUpgradeCost(BigDecimal amountToCharge) {
        log.debug("Validando costo de upgrade: {}", amountToCharge);
        if (amountToCharge.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("El cambio de plan no requiere un pago adicional o es un downgrade (monto: {}).", amountToCharge);
            throw new IllegalArgumentException("Este flujo no soporta cambios a planes de menor o igual valor. Monto calculado: " + amountToCharge);
        }
    }

    public static void validatePlanUpgrade(PlanEntity currentPlan, PlanEntity newPlan) {
        log.debug("Validando si el plan actual ('{}') y el nuevo ('{}') son diferentes.", currentPlan.getName(), newPlan.getName());
        if (currentPlan.getId().equals(newPlan.getId())) {
            throw new IllegalArgumentException("El usuario ya está suscrito al plan " + newPlan.getName());
        }
    }

}
