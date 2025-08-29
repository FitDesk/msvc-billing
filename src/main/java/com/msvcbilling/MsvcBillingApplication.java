package com.msvcbilling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class MsvcBillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsvcBillingApplication.class, args);
    }

}
