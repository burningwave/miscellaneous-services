/*
 * This file is part of Burningwave Miscellaneous Services.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: https://github.com/burningwave/miscellaneous-services
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roberto Gentili
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.burningwave.services;

import static org.burningwave.core.assembler.StaticComponentContainer.Methods;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.burningwave.Badge;
import org.burningwave.DBBasedCache;
import org.burningwave.FSBasedCache;
import org.burningwave.SimpleCache;
import org.burningwave.Utility;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@SpringBootApplication
@EnableAutoConfiguration(exclude = {
	DataSourceAutoConfiguration.class,
	HibernateJpaAutoConfiguration.class
})
@EnableScheduling
@EnableAsync
public class Application extends SpringBootServletInitializer {
	private final static org.slf4j.Logger logger;

	final static String SCHEME_AND_HOST_NAME_CACHE_KEY = "SchemeAndHostname";
	String schemeAndHostName;

    static {
    	logger = org.slf4j.LoggerFactory.getLogger(Application.class);
    }

	public static void main(String[] args) throws IOException {
		SpringApplication.run(Application.class, args);
	}


	@Bean("badge")
	public Badge badge(
		@Qualifier("utility") Utility utility
	) {
		return new Badge(utility);
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


	@Bean("burningwave.core.staticComponentContainer.config")
	@ConfigurationProperties("burningwave.core.static-component-container")
	public Map<String, String> staticComponentContainerConfig(){
		return new LinkedHashMap<>();
	}


	@Bean
	public Class<StaticComponentContainer> staticComponentContainer(@Qualifier("burningwave.core.staticComponentContainer.config") Map<String, String> configMap) {
		StaticComponentContainer.Configuration.Default.add(configMap);
		return StaticComponentContainer.class;
	}


	@Bean("cache")
	@ConditionalOnExpression(value = "'${cache.type}'.trim().equalsIgnoreCase('File system based')")
	public SimpleCache cache(
		@Qualifier("cacheConfig") Map<String, String> configMap
	) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
		Map<String, Object> configuration = new HashMap<>();
		configuration.putAll(configMap);
		return new FSBasedCache(configuration);
	}


	@Bean("nexusConnectorGroup.config")
	@ConfigurationProperties("nexus-connector.group")
	public Map<String, String> nexusConnectorConfig(){
		return new LinkedHashMap<>();
	}


	@Bean("nexusConnectorGroup")
	@ConditionalOnProperty(prefix = "nexus-connector.group", name = "enabled", havingValue = "true")
	public NexusConnector.Group nexusConnector(
		@Qualifier("cache") SimpleCache cache,
		@Qualifier("restTemplate") RestTemplate restTemplate,
		@Qualifier("utility") Utility utility,
		@Qualifier("nexusConnectorGroup.config") Map<String, String> configMap
	) throws JAXBException, ParseException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException, IOException {
		Map<String, Object> configuration = new HashMap<>();
		configuration.putAll(configMap);
		return new NexusConnector.Group(cache, restTemplate, utility, configuration);
	}


	@Bean("gitHubConnector.config")
	@ConfigurationProperties("github-connector")
	public Map<String, String> gitHubConnectorConfig(){
		return new LinkedHashMap<>();
	}


	@Bean("gitHubConnector")
	@ConditionalOnProperty(prefix = "github-connector", name = "enabled", havingValue = "true")
	GitHubConnector gitHubConnector(
		@Qualifier("gitHubConnector.config") Map<String, String> configMap,
		SimpleCache cache
	) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
		Map<String, Object> configuration = new HashMap<>();
		configuration.putAll(configMap);
		GitHubConnector gitHubConnector = new GitHubConnector(configuration);
		return gitHubConnector;
	}


	@Bean("herokuConnector.config")
	@ConfigurationProperties("heroku-connector")
	public Map<String, String> herokuConnectorConfig(){
		return new LinkedHashMap<>();
	}


	@Bean("herokuConnector")
	@ConditionalOnExpression(value = "'${heroku-connector.authorization.token}' != null && '${heroku-connector.remote.authorization.token}' != null")
	public HerokuConnector herokuConnector(
		@Qualifier("herokuConnector.config") Map<String, String> configMap
	) {
		HerokuConnector connector = new HerokuConnector(configMap);
		return connector;
	}


	@Bean("applicationSelfConnector.config")
	@ConfigurationProperties("application.self-connector")
	public Map<String, String> applicationSelfConnectorConfig(){
		return new LinkedHashMap<>();
	}


	@Bean("applicationSelfConnector")
	public SelfConnector applicationSelfConnector(
		Application application,
		@Qualifier("cache") SimpleCache cache,
		@Qualifier("applicationSelfConnector.config") Map<String, String> configMap
	) {
		Map<String, Object> configuration = new HashMap<>();
		configuration.putAll(configMap);
		return new SelfConnector(application, cache, configuration);
	}


	@Bean("restTemplate")
	public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        restTemplate.setRequestFactory(requestFactory);
        return restTemplate;
	}


	@Bean
	public WebMvcConfigurer webMvcConfigurer(Application application) {
		return new WebMvcConfigurer(application);
	}


	@Bean("scheduledOperations.config")
	@ConfigurationProperties("scheduled-operations")
	public Map<String, Map<String, String>> scheduledOperationsConfig(){
		return new LinkedHashMap<>();
	}


	@Bean("scheduledOperations")
	public Collection<ScheduledFuture<?>> scheduledOperations(
		ApplicationContext applicationContext,
		TaskScheduler taskScheduler,
		@Qualifier("scheduledOperations.config")Map<String, Map<String, String>> config
	) {
		Collection<ScheduledFuture<?>> scheduledOperations = new ArrayList<>();
		for (Map<String, String> jobConfig : config.values()) {
			if (!jobConfig.get("cron").trim().equals("-")) {
				try {
					String[] targetAndMethod = jobConfig.get("executable").split("\\.");
					Object target = applicationContext.getBean(targetAndMethod[0]);
					scheduledOperations.add(
						taskScheduler.schedule(
							() -> Methods.invokeDirect(target, targetAndMethod[1]),
							new CronTrigger(jobConfig.get("cron"), TimeZone.getTimeZone(jobConfig.get("zone")))
						)
					);
				} catch (Throwable exc) {
					logger.warn("Could not schedule operation {}: {}", jobConfig.get("executable"), exc.getMessage());
				}
			} else {
				logger.info("Scheduled operation {} is disabled", jobConfig.get("executable"));
			}
		}
		return scheduledOperations;
	}

	public static class WebMvcConfigurer implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
		private Application application;

		public WebMvcConfigurer(Application application) {
			this.application = application;
		}

		@Override
		public void addInterceptors(InterceptorRegistry registry) {
			registry.addInterceptor(new HandlerInterceptor() {
				@Override
				public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
					String applicationSchemeAndHostName = ServletUriComponentsBuilder.fromCurrentContextPath().build().toString();
					if (applicationSchemeAndHostName != application.schemeAndHostName) {
						synchronized(application) {
							if (applicationSchemeAndHostName != application.schemeAndHostName) {
								if (applicationSchemeAndHostName != application.schemeAndHostName) {
									application.schemeAndHostName = applicationSchemeAndHostName;
									application.notifyAll();
								}
							}
						}
					}
					return HandlerInterceptor.super.preHandle(request, response, handler);
				}
			});
		}


		@Bean("containerCustomizer")
		public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> containerCustomizer() {
			return container -> {
				container.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/miscellaneous-services/stats/artifact-download-chart"));
			};
		}

	}

	public static class SelfConnector {

		final RestTemplate restTemplate;
	    final HttpEntity<String> entity;
	    final Supplier<String> getStatsTotalDownloadsUriComponentsBuilder;

	    public SelfConnector(Application application, SimpleCache cache, Map<String, Object> configMap) {
	    	restTemplate = new RestTemplate();
	        entity = new HttpEntity<String>(new HttpHeaders());
	        getStatsTotalDownloadsUriComponentsBuilder = () -> {
	        	return application.getURL("/miscellaneous-services/stats/total-downloads?groupId=org.burningwave&artifactId=core");
	        };
	    }

	    public void ping() {
			String url = getStatsTotalDownloadsUriComponentsBuilder.get();
			if (url != null) {
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


	@Import({
		DataSourceAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class
	})
	@EnableJpaRepositories(basePackages = {"org.burningwave"}, considerNestedRepositories = true)
	@EntityScan(basePackages = {"org.burningwave"})
	@Conditional(DBConfig.Condition.class)
	public static class DBConfig {

		@Bean("cache")
		public SimpleCache cache(
			@Qualifier("cacheConfig") Map<String, String> configMap
		) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
			Map<String, Object> configuration = new HashMap<>();
			configuration.putAll(configMap);
			return new DBBasedCache(configuration);
		}

		public static class Condition implements org.springframework.context.annotation.Condition {

			@Override
			public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		        return "Database based".equalsIgnoreCase(context.getEnvironment().getProperty("cache.type").trim());
			}

		}

	}

	public String getURL(String relativePath) {
		return Optional.ofNullable(schemeAndHostName).map(url -> url + relativePath).orElseGet(() -> null);
	}

}
