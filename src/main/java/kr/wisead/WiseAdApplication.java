package kr.wisead;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WiseAdApplication {

	public static void main(String[] args) {
		SpringApplication.run(WiseAdApplication.class, args);
	}

}
