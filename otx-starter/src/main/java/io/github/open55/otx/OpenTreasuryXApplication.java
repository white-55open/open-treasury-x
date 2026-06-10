package io.github.open55.otx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class OpenTreasuryXApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpenTreasuryXApplication.class, args);
	}

}
