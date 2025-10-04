package com.msvcbilling.mappers;

import com.msvcbilling.config.MapStructConfig;
import com.msvcbilling.dtos.PaymentResponse;
import com.msvcbilling.entities.PaymentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(config = MapStructConfig.class)
public interface PaymentMapper {

    @Mapping(source = "amount", target = "transactionAmount")
    PaymentResponse entityToResponse(PaymentEntity entity);

    @Mapping(source = "transactionAmount", target = "amount")
    PaymentEntity responseToEntity(PaymentResponse response);
}