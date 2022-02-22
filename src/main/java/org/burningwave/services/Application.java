package org.burningwave.services;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;

import org.burningwave.Badge;
import org.burningwave.SimpleCache;
import org.burningwave.Utility;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@SpringBootApplication
//Uncomment this to use the FSBasedCache
//@EnableAutoConfiguration(exclude = {
//	org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
//	org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
//})
//Comment this to use the FSBasedCache
@EnableJpaRepositories(basePackages = {"org.burningwave"}, considerNestedRepositories = true)
//Comment this to use the FSBasedCache
@EntityScan(basePackages = {"org.burningwave"})
@EnableScheduling
@EnableAsync
public class Application {
	final static String SCHEME_AND_HOST_NAME_CACHE_KEY = "SchemeAndHostname";
	private static ApplicationContext applicationContext;
	String schemeAndHostName;

	public static void main(String[] args) throws IOException {
		applicationContext = SpringApplication.run(Application.class, args);
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
	public SimpleCache cache(
		@Qualifier("cacheConfig") Map<String, String> configMap
	) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
		Map<String, Object> configuration = new HashMap<>();
		configuration.putAll(configMap);
		return (SimpleCache)Class.forName(configMap.get("type")).getDeclaredConstructor(Map.class).newInstance(configuration);
	}

	@Bean("nexusConnectorGroup.config")
	@ConfigurationProperties("nexus-connector")
	public Map<String, String> nexusConnectorConfig(){
		return new LinkedHashMap<>();
	}

	@Bean("nexusConnectorGroup")
	@ConditionalOnProperty(prefix = "nexus-connector.group", name = "enabled", havingValue = "true")
	public NexusConnector.Group nexusConnector(
		@Qualifier("cache") SimpleCache cache,
		@Qualifier("utility") Utility utility,
		@Qualifier("nexusConnectorGroup.config") Map<String, String> configMap
	) throws UnsupportedEncodingException, JAXBException, ParseException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
		Map<String, Object> configuration = new HashMap<>();
		configuration.putAll(configMap);
		return new NexusConnector.Group(cache, utility, configuration);
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

	@Bean("applicationSelfConnector.config")
	@ConfigurationProperties("application.self-connector")
	public Map<String, String> applicationSelfConnectorConfig(){
		return new LinkedHashMap<>();
	}

	@Bean("applicationSelfConnector")
	@ConditionalOnExpression(value = "!'${scheduled-operations.ping.cron}'.trim().equals('-')")
	public SelfConnector applicationSelfConnector(
		Application application,
		@Qualifier("cache") SimpleCache cache,
		@Qualifier("applicationSelfConnector.config") Map<String, String> configMap
	) {
		Map<String, Object> configuration = new HashMap<>();
		configuration.putAll(configMap);
		return new SelfConnector(application, cache, configuration);
	}

	@Bean
	public WebMvcConfigurer webMvcConfigurer(Application application, @Qualifier("cache") SimpleCache cache) {
		return new WebMvcConfigurer(application, cache);
	}

	@Scheduled(
		cron = "${scheduled-operations.ping.cron}",
		zone = "${scheduled-operations.ping.zone}"
	)
	@Async
	public void ping() throws Throwable {
		applicationContext.getBean(SelfConnector.class).ping();
	}

	public static class WebMvcConfigurer implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
		private SimpleCache cache;
		private Application application;

		public WebMvcConfigurer(Application application, SimpleCache cache) {
			this.cache = cache;
			this.application = application;
		}

		@Override
		public void addInterceptors(InterceptorRegistry registry) {
			registry.addInterceptor(new HandlerInterceptor() {
				@Override
				public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
					if (application.schemeAndHostName == null) {
						synchronized(application) {
							if (application.schemeAndHostName == null) {
								application.schemeAndHostName = cache.load(Application.SCHEME_AND_HOST_NAME_CACHE_KEY);
								if (application.schemeAndHostName == null) {
									application.schemeAndHostName = ServletUriComponentsBuilder.fromCurrentContextPath().build().toString();
									cache.store(Application.SCHEME_AND_HOST_NAME_CACHE_KEY, application.schemeAndHostName);
								}
							}
						}
					}
					return HandlerInterceptor.super.preHandle(request, response, handler);
				}
			});
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
	        	if (application.schemeAndHostName == null) {
	        		synchronized(application) {
	        			if (application.schemeAndHostName == null) {
			        		application.schemeAndHostName = cache.load(Application.SCHEME_AND_HOST_NAME_CACHE_KEY);
			        		if (application.schemeAndHostName == null) {
			        			return null;
			        		}
	        			}
	        		}
	        	}
	        	return application.schemeAndHostName + "/miscellaneous-services/stats/total-downloads?groupId=org.burningwave&artifactId=core";
	        };
	    }

	    public void ping() throws Throwable {
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
}
