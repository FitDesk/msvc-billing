package com.msvcbilling.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageUploadResponseDto {
    private String url;
    private String publicId;
    private String format;
    private Long size;
    private Integer width;
    private Integer height;
}
