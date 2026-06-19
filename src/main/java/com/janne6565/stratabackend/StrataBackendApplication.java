package com.janne6565.stratabackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StrataBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(StrataBackendApplication.class, args);
	}

}
