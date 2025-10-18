package com.msvcbilling.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {
    @Value("${cloudinary.cloud_name}")
    private String cloudName;
    @Value("${cloudinary.api_key}")
    private String apikey;
    @Value("${cloudinary.api_secret}")
    private String apiSecret;


    @Bean
    public Cloudinary cloudinary() {
        if (cloudName.isBlank() || apikey.isBlank() || apiSecret.isBlank()) {
            throw new IllegalStateException("Faltan propiedades de Cloudinary: configura cloudinary.cloud_name, cloudinary.api_key y cloudinary.api_secret");
        }
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apikey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }
}
