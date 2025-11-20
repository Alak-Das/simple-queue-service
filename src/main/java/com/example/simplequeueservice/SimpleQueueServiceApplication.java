package com.example.simplequeueservice;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Simple Queue Service API", version = "1.0", description = "API for a simple queue service"))
public class SimpleQueueServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SimpleQueueServiceApplication.class, args);
	}

}
