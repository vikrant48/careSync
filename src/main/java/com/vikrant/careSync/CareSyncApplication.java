package com.vikrant.careSync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class  CareSyncApplication {

	public static void main(String[] args) {
		SpringApplication.run(CareSyncApplication.class, args);
	}

}
