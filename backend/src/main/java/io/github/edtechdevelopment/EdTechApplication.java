package io.github.edtechdevelopment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EdTechApplication {

    public static void main(String[] args) {
        SpringApplication.run(EdTechApplication.class, args);
    }
}
