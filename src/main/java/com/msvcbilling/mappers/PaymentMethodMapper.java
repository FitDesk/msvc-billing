package com.msvcbilling.mappers;


import com.msvcbilling.config.MapStructConfig;
import com.msvcbilling.dtos.card.SavedPaymentMethodDto;
import com.msvcbilling.entities.PaymentMethodEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface PaymentMethodMapper {

    @Mapping(target = "isExpired", expression = "java(entity.isExpired())")
    @Mapping(target = "displayName", expression = "java(getDisplayName(entity))")
    SavedPaymentMethodDto toDto(PaymentMethodEntity entity);

    List<SavedPaymentMethodDto> toDtoList(List<PaymentMethodEntity> entities);

    @Named("getDisplayName")
    default String getDisplayName(PaymentMethodEntity entity) {
        return String.format("%s ****%s",
                entity.getCardBrand().toUpperCase(),
                entity.getLastFourDigits());
    }
}