package com.stockpile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan // picks up @ConfigurationProperties records (e.g. PutawayWeights)
public class StockpileBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(StockpileBackendApplication.class, args);
	}

}
