package com.vikrant.careSync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import static java.util.TimeZone.getTimeZone;
import static java.util.TimeZone.setDefault;

@SpringBootApplication
@EnableScheduling
public class  CareSyncApplication {

	public static void main(String[] args) {
		// Appointment times are stored in IST; on a UTC host (Render) "now" would be 5h30m behind
		setDefault(getTimeZone("Asia/Kolkata"));
		SpringApplication.run(CareSyncApplication.class, args);
	}

}
