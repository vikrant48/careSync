package com.vikrant.careSync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class  CareSyncApplication {

	public static void main(String[] args) {
		// Appointment times are stored in IST; on a UTC host (Render) "now" would be 5h30m behind
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(CareSyncApplication.class, args);
	}

}
