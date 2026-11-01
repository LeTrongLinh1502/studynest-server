package com.studynest.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StudynestServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(StudynestServerApplication.class, args);
	}

}
