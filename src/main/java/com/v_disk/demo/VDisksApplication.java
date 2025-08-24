package com.v_disk.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = "com.v_disk")
@EnableMongoRepositories(basePackages = "com.v_disk.repository")
public class VDisksApplication {

	public static void main(String[] args) {
		SpringApplication.run(VDisksApplication.class, args);
	}

}
