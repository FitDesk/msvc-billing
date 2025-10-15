package com.msvcbilling.services.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.msvcbilling.dtos.ImageUploadResponseDto;
import com.msvcbilling.exceptions.ImageUploadException;
import com.msvcbilling.exceptions.InvalidImageFormatException;
import com.msvcbilling.services.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    private final Cloudinary cloudinary;

    private static final String BILLING_FOLDER = "fitdesk/billing";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; //-> 5MB

    @Override
    public ImageUploadResponseDto uploadProfileImage(MultipartFile file, UUID planId) {
        log.info("Subiendo imagen de perfil para membresia: {}", planId);
        validateImage(file);

        try {
            String publicId = generatePublicIdForMember(planId);
            Transformation transformation = new Transformation()
//                    .width(400)
//                    .height(400)
                    .crop("fill")
                    .gravity("face")
                    .quality("auto:good")
                    .fetchFormat("auto");


            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "public_id", publicId,
                    "folder", BILLING_FOLDER,
                    "transformation", transformation,
                    "overwrite", true,
                    "resource_type", "image"
            );

            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);

            ImageUploadResponseDto response = mapToResponseDto(uploadResult);
            log.info("Imagen de membresia guarada en {}", BILLING_FOLDER);
            log.info("URL: {}", response.getUrl());
            return response;
        } catch (
                IOException e) {
            log.error("Error al guardar la imagen de membresia {}: {}", planId, e.getMessage());
            throw new ImageUploadException("Error al cargar la imagen ", e);
        }

    }

    @Override
    public boolean deleteImage(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            log.warn("Intento de membresia imagen con public nulo");
            return false;
        }


        try {
            log.info("Eliminando imagen de membresia con publicId: {}", publicId);
            Map result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());

            String resultStatus = (String) result.get("result");

            boolean deleted = "ok".equals(resultStatus);

            if (deleted) {
                log.info("Imagen de membresia eliminada de cloduinary");
            } else {
                log.warn("No se pudo elimnar la imagen");
            }
            return deleted;

        } catch (
                IOException e) {
            log.error("Error al eliminar la imagen {} : {}", publicId, e.getMessage());
            return false;
        }

    }

    @Override
    public ImageUploadResponseDto updateProfileImage(MultipartFile file, UUID planId, String oldPublicId) {
        log.info("Actualizando imagen de la membresia {}", planId);

        if (oldPublicId != null && !oldPublicId.isBlank()) {
            deleteImage(oldPublicId);
        }
        return uploadProfileImage(file, planId);

    }


    @Override
    public String extractPublicIdFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }


        try {
            String[] parts = imageUrl.split("/upload/");
            if (parts.length > 1) {
                String pathWithVersion = parts[1];
                String path = pathWithVersion.replaceFirst("v\\d+/", "");
                int lastDot = path.lastIndexOf('.');
                return lastDot > 0 ? path.substring(0, lastDot) : path;
            }
        } catch (
                Exception e) {
            log.warn("No se pudo extraer publicId de: {}", imageUrl);
        }

        return null;
    }

    private String generatePublicIdForMember(UUID planId) {
        return String.format("%s_%s", planId, System.currentTimeMillis());
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageFormatException("El archivo está vacío");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidImageFormatException(
                    String.format("El archivo excede el tamaño máximo de %d MB", MAX_FILE_SIZE / 1024 / 1024)
            );
        }

        String extension = FilenameUtils.getExtension(file.getOriginalFilename());
        assert extension != null;
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new InvalidImageFormatException(
                    "Formato no permitido. Válidos: " + String.join(", ", ALLOWED_EXTENSIONS)
            );
        }
    }

    private ImageUploadResponseDto mapToResponseDto(Map uploadResult) {
        return ImageUploadResponseDto.builder()
                .url((String) uploadResult.get("secure_url"))
                .publicId((String) uploadResult.get("public_id"))
                .format((String) uploadResult.get("format"))
                .size(((Number) uploadResult.get("bytes")).longValue())
                .width((Integer) uploadResult.get("width"))
                .height((Integer) uploadResult.get("height"))
                .build();
    }


}
