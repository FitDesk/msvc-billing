package com.msvcbilling.mappers;

import com.msvcbilling.config.MapStructConfig;
import com.msvcbilling.dtos.payment.PaymentDetailsResponseDto;
import com.msvcbilling.dtos.payment.PaymentResponse;
import com.msvcbilling.entities.PaymentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;


@Mapper(config = MapStructConfig.class)
public interface PaymentMapper {

    @Mapping(source = "amount", target = "transactionAmount")
    PaymentResponse entityToResponse(PaymentEntity entity);

    @Mapping(source = "transactionAmount", target = "amount")
    PaymentEntity responseToEntity(PaymentResponse response);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "payerEmail", target = "payerEmail")
    @Mapping(source = "paymentMethodId", target = "paymentMethodId")
    @Mapping(source = "plan.name", target = "planName")
    @Mapping(source = "amount", target = "amount")
    @Mapping(source = "entity", target = "planExpirationDate", qualifiedByName = "calculateExpirationDate")
    @Mapping(source = "dateApproved", target = "paymentDate", qualifiedByName = "formatPaymentDate")
    @Mapping(source = "status", target = "status")
    PaymentDetailsResponseDto toDto(PaymentEntity entity);

    @Named("calculateExpirationDate")
    default String calculateExpirationDate(PaymentEntity entity) {
        if (entity.getDateApproved() == null || entity.getPlan() == null || entity.getPlan().getDurationMonths() == null) {
            return "N/A";
        }
        OffsetDateTime expiration = entity.getDateApproved().plusMonths(entity.getPlan().getDurationMonths());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", new Locale("es", "ES"));
        return expiration.format(formatter);
    }

    @Named("formatPaymentDate")
    default String formatPaymentDate(OffsetDateTime dateApproved) {
        if (dateApproved == null) {
            return "N/A";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd 'de' MMM 'de' yyyy, HH:mm", new Locale("es", "ES"));
        return dateApproved.format(formatter);
    }

}