package com.msvcbilling.mappers;

import com.msvcbilling.config.MapStructConfig;
import com.msvcbilling.dtos.PlanResponseDto;
import com.msvcbilling.entities.PlanEntity;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface PlanMapper {
    PlanResponseDto entityToDto(PlanEntity entity);
}
