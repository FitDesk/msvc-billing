package com.msvcbilling.services;

import com.msvcbilling.dtos.ImageUploadResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface CloudinaryService {
    ImageUploadResponseDto uploadProfileImage(MultipartFile file, UUID planId);
    boolean deleteImage(String publicId);
    ImageUploadResponseDto updateProfileImage(MultipartFile file,UUID planId,String oldPublicId);
    String extractPublicIdFromUrl(String imageUrl);
}
