package org.burningwave.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
@ImportAutoConfiguration(Application.Environment.class)
public class Batch implements CommandLineRunner {

	public static void main(String[] args) {
		try {
			new SpringApplicationBuilder(Batch.class).web(WebApplicationType.NONE).run(args);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Autowired
	private RestController restController;

	@Override
	public void run(String... args) throws Exception {
		try {
			restController.getTotalDownloads(null, null, null, null, null);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
