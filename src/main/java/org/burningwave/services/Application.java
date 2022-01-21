package org.burningwave.services;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.xml.bind.JAXBException;

import org.burningwave.Badge;
import org.burningwave.SimpleCache;
import org.burningwave.Utility;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootApplication
//@EnableAutoConfiguration(exclude = {
//	org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
//	org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
//})
@EnableJpaRepositories(basePackages = {"org.burningwave"}, considerNestedRepositories = true)
@EntityScan(basePackages = {"org.burningwave"})
@EnableScheduling
@EnableAsync
public class Application {
	private static ApplicationContext applicationContext;

	public static void main(String[] args) throws IOException {
		applicationContext = SpringApplication.run(Application.class, args);
	}

	@Bean("badge")
	public Badge badge() {
		return new Badge();
	}

	@Bean("utility")
	public Utility utility() {
		return new Utility();
	}

	@Bean("cacheConfig")
	@ConfigurationProperties("cache")
	public Map<String, String> cacheConfig(){
		return new LinkedHashMap<>();
	}

	@Bean("burningwave.core.static-component-container.config")
	@ConfigurationProperties("burningwave.core.static-component-container")
	public Map<String, String> staticComponentContainerConfig(){
		return new LinkedHashMap<>();
	}

	@Bean
	public Class<StaticComponentContainer> staticComponentContainer(@Qualifier("burningwave.core.static-component-container.config") Map<String, String> configMap) {
		StaticComponentContainer.Configuration.addValues(configMap);
		return StaticComponentContainer.class;
	}

	@Bean("cache")
	public SimpleCache cache(
		@Qualifier("cacheConfig") Map<String, String> configMap
	) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
		Map<String, Object> configuration = new HashMap<>();
		configuration.putAll(configMap);
		return (SimpleCache)Class.forName(configMap.get("type")).getDeclaredConstructor(Map.class).newInstance(configuration);
	}

	@Bean("nexusConnector.config")
	@ConfigurationProperties("nexus-connector")
	public Map<String, String> nexusConnectorConfig(){
		return new LinkedHashMap<>();
	}

	@Bean("nexusConnector")
	public NexusConnector nexusConnector(
		@Qualifier("nexusConnector.config") Map<String, String> configMap
	) throws UnsupportedEncodingException, JAXBException, ParseException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
		Map<String, Object> configuration = new HashMap<>();
		configuration.putAll(configMap);
		return new NexusConnector(configuration);
	}

	@Bean("gitHubConnector.config")
	@ConfigurationProperties("github-connector")
	public Map<String, String> gitHubConnectorConfig(){
		return new LinkedHashMap<>();
	}

	@Bean("gitHubConnector")
	GitHubConnector gitHubConnector(
		@Qualifier("gitHubConnector.config") Map<String, String> configMap
	) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
		Map<String, Object> configuration = new HashMap<>();
		configuration.putAll(configMap);
		return new GitHubConnector(configuration);
	}

	@Bean("applicationSelfConnector.config")
	@ConfigurationProperties("application-self-connector")
	public Map<String, String> applicationSelfConnectorConfig(){
		return new LinkedHashMap<>();
	}

	@Bean("applicationSelfConnector")
	public SelfConnector applicationSelfConnector(
		@Qualifier("applicationSelfConnector.config") Map<String, String> configMap
	) {
		Map<String, Object> configuration = new HashMap<>();
		configuration.putAll(configMap);
		return new SelfConnector(configuration);
	}

	@Scheduled(
		cron = "${scheduled-operations.ping.cron}",
		zone = "${scheduled-operations.ping.zone}"
	)
	@Async
	public void ping() {
		applicationContext.getBean(SelfConnector.class).ping();
	}

	public static class SelfConnector {

		final RestTemplate restTemplate;
	    final HttpEntity<String> entity;
	    final Supplier<UriComponentsBuilder> getStatsTotalDownloadsUriComponentsBuilder;

	    public SelfConnector(Map<String, Object> configMap)  {
	    	restTemplate = new RestTemplate();
	        entity = new HttpEntity<String>(new HttpHeaders());
	        getStatsTotalDownloadsUriComponentsBuilder = () ->
	        	UriComponentsBuilder.newInstance()
	        	.scheme("https")
	        	.host((String)configMap.get("host"))
	        	.path("/miscellaneous-services/stats/total-downloads")
	        	.queryParam("artifactId", "core");
	    }

	    public void ping() {
			String url = getStatsTotalDownloadsUriComponentsBuilder.get().build().toString();
			restTemplate.exchange(
				url,
				HttpMethod.GET,
				entity,
				Long.class
			);
			org.slf4j.LoggerFactory.getLogger(SelfConnector.class).info("Ping to url '{}' done", url);
		}
	}
}
