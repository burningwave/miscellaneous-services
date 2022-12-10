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

import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.burningwave.SimpleCache;
import org.burningwave.Throwables;
import org.burningwave.Utility;
import org.burningwave.services.NexusConnector.Project.Artifact;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;


@SuppressWarnings("unchecked")
public class NexusConnector {
	private final static org.slf4j.Logger logger;
	private static Pattern latestReleasePattern;

	private RestTemplate restTemplate;
	private HttpEntity<String> entity;
	private JAXBContext jaxbContext;
	private Supplier<UriComponentsBuilder> getStatsUriComponentsBuilder;
	private Collection<Project> allProjects;
	private Map<String, Object> inMemoryCache;
	private long timeToLiveForInMemoryCache;
	private int dayOfTheMonthFromWhichToLeave;
    private SimpleCache cache;
    private Utility utility;



    static {
    	logger = org.slf4j.LoggerFactory.getLogger(NexusConnector.class);
    	latestReleasePattern = Pattern.compile("<latestRelease>(.*?)<\\/latestRelease>");
    }

    public NexusConnector(RestTemplate restTemplate, SimpleCache cache, Utility utility, Configuration nexusConfiguration) throws JAXBException, ParseException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException, JsonProcessingException {
    	this.restTemplate = restTemplate;
    	this.cache = cache;
    	this.utility = utility;
    	HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", nexusConfiguration.getAuthorization().getToken().getType() + " " + nexusConfiguration.getAuthorization().getToken().getValue());
        entity = new HttpEntity<String>(headers);
        jaxbContext = JAXBContext.newInstance(
    		GetGroupListOutput.class,
    		GetArtifactListOutput.class,
    		GetStatsOutput.class
    	);
		String username = new String(
			Base64.getDecoder().decode(
				entity.getHeaders().get("Authorization").iterator().next().split("\\s")[1]
			), StandardCharsets.UTF_8
		).split(":")[0];
		String configurationObjectsKey = nexusConfiguration.getClass() + ";" + nexusConfiguration.getHost() + ";" + username;
        Object[] configurationObjectsFromCache = cache.load(configurationObjectsKey);
        if (configurationObjectsFromCache == null) {
        	configurationObjectsFromCache = new Object[2];
        }
        setHost(nexusConfiguration, configurationObjectsFromCache, username);
        setProjectInfos(nexusConfiguration, configurationObjectsFromCache);
        cache.store(configurationObjectsKey, configurationObjectsFromCache);
        logger.info("Projects configuration: {}", allProjects);
        inMemoryCache = new ConcurrentHashMap<>();
        timeToLiveForInMemoryCache = nexusConfiguration.getCache().getTtl();
        dayOfTheMonthFromWhichToLeave = nexusConfiguration.getCache().getDayOfTheMonthFromWhichToLeave();
    }

	public void setHost(Configuration nexusConfiguration, Object[] configurationObjectsFromCache, String username) throws JAXBException {
		String[] hosts = nexusConfiguration.getHost().split("\\|");
		for (String host : hosts) {
    		getStatsUriComponentsBuilder = () ->
    			UriComponentsBuilder.newInstance().scheme(nexusConfiguration.getScheme()).host(host);
    		try {
				callGetGroupListRemote();
				logger.info("Login successful on {} for user {}", host, username);
				configurationObjectsFromCache[0] = new String[] {nexusConfiguration.getScheme(), host};
			} catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException exc) {
				logger.info("Unable to login {} on {}: {}", username, host, exc.getMessage());
				if (host == hosts[hosts.length - 1]) {
					if (configurationObjectsFromCache[0] != null) {
						String[] schemeAndHost = (String[])configurationObjectsFromCache[0];
						logger.info("Loading scheme and host from cache");
						getStatsUriComponentsBuilder = () ->
							UriComponentsBuilder.newInstance().scheme(schemeAndHost[0]).host(schemeAndHost[1]);
					} else {
						throw exc;
					}
				}
			}
    	}
		if (configurationObjectsFromCache[0] != null ) {
			getStatsUriComponentsBuilder = () ->
				UriComponentsBuilder.newInstance().scheme(((String[])configurationObjectsFromCache[0])[0]).host(((String[])configurationObjectsFromCache[0])[1]);
		}
	}

	private void setProjectInfos(Configuration nexusConfiguration, Object[] configurationObjectsFromCache) throws ParseException, JAXBException, JsonMappingException, JsonProcessingException {
		try {
			GetGroupListOutput groupList = callGetGroupListRemote();
			Collection<Project> projectsInfo = new CopyOnWriteArrayList<>();
			for (GetGroupListOutput.Data.Group group : groupList.getData().getGroups()) {
				Project project = new Project();
				projectsInfo.add(project);
				project.setId(group.getId());
				project.setName(group.getName());
				Collection<Project.Artifact> artifacts = new CopyOnWriteArrayList<>();
	        	project.setArtifacts(artifacts);
				GetStatsInput input = new GetStatsInput();
				input.setGroupId(group.getId());
				input.setProjectId(group.getName());
				GetArtifactListOutput artifactList = callGetArtifactListRemote(input);
				for (String artifactName : artifactList.getData().getArtifacts()) {
					Project.Artifact artifact = new Project.Artifact();
					artifacts.add(artifact);
					artifact.setName(artifactName);
					artifact.setAlias(artifactName);
					artifact.setColor(utility.randomHex());
					artifact.setSite("https://maven-badges.herokuapp.com/maven-central/" + project.getName() + "/" + artifactName + "/");
				}
			}
			configurationObjectsFromCache[1] = projectsInfo;
		} catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException exc) {
			logger.warn("Unable to retrieve project informations from remote: {}", exc.getMessage());
			if (configurationObjectsFromCache[1] != null) {
				logger.info("Setting project informations from cache");
			} else {
				throw exc;
			}
		}
		this.allProjects = (Collection<Project>)configurationObjectsFromCache[1];
		Collection<Project> projects = nexusConfiguration.getProject();
		if (projects != null) {
			for (Project projectFromConfig : projects) {
				Project project = getProject(this.allProjects, projectFromConfig.getName());
				if (project == null) {
					throw new IllegalArgumentException("Project named " + projectFromConfig.getName() + " not found on Nexus");
				}
				for (Project.Artifact artifactFromConfig : projectFromConfig.getArtifacts()) {
					Project.Artifact artifact = getArtifactForName(project, artifactFromConfig.getName());
					if (artifact == null) {
						throw new IllegalArgumentException("Artifact named " + artifactFromConfig.getName() + " not found on Nexus");
					}
					utility.setIfNotNull(artifact::setAlias, artifactFromConfig::getAlias);
					utility.setIfNotNull(artifact::setColor, artifactFromConfig::getColor);
					utility.setIfNotNull(artifact::setSite, artifactFromConfig::getSite);
				}
			}
		}
		Calendar startDateAsCalendar = nexusConfiguration.getStartDate();
		for (Project project : allProjects) {
			project.setStartDate(startDateAsCalendar);
		}
	}

	public void clearCache() {
		synchronized(inMemoryCache) {
			inMemoryCache.clear();
			logger.info("In memory cache cleaning done");
		}
	}

	public Collection<String[]> getAllProjectInfos() {
		Collection<String[]> projectInfos = new ArrayList<>();
		for (Project project : allProjects) {
			for (Artifact artifact : project.getArtifacts()) {
				projectInfos.add(new String[]{project.getName() + ":" + artifact.getName(), artifact.getAlias(), artifact.getColor(), artifact.getSite()});
			}
		}
		return projectInfos;
	}

    public GetStatsOutput getStats(GetStatsInput input) {
		String key = getKey(input);
		GetStatsOutput output = (GetStatsOutput)inMemoryCache.get(key);
		if (output == null) {
			output = cache.load(key);
			if (output != null) {
				inMemoryCache.put(key, output);
			}
		}
		if (output != null) {
			if ((new Date().getTime() - output.getTime().getTime()) <= timeToLiveForInMemoryCache) {
    			return output;
    		}
		}
		GetStatsOutput oldOutput = output;
		return Synchronizer.execute(Objects.getId(this) + key, () -> {
    		GetStatsOutput newOutput;
			try {
				newOutput = callGetStatsRemote(input);
			} catch (Throwable exc) {
				if (oldOutput != null) {
					return oldOutput;
				}
				return Throwables.rethrow(exc);
			}
    		Calendar newDate = utility.newCalendarAtTheStartOfTheMonth();
			newDate.set(Calendar.DATE, dayOfTheMonthFromWhichToLeave);
			Runnable storer = () -> cache.storeAndNotify(key, newOutput, oldOutput);
			if (Integer.valueOf(0).equals(newOutput.getData().getTimeline().getValues().stream().reduce((prev, next) -> next).orElse(null))) {
				if (oldOutput != null) {
	    			newDate.setTime(oldOutput.getTime());
	    			newDate.add(Calendar.DATE, 1);
				} else {
					newDate.add(Calendar.MONTH, -1);
				}
    			if (!isStartDateEqualsToDefaultValue(input) && !isMonthsEqualsToDefaultValue(input)) {
    				storer = () -> cache.store(key, newOutput);
    			}
			}
    		newOutput.setTime(newDate.getTime());
    		storer.run();
			inMemoryCache.put(key, newOutput);
			return newOutput;
		});

    }

	private boolean isMonthsEqualsToDefaultValue(GetStatsInput input) {
		return computeDefaultMonths(input.getStartDate()) == input.getMonths();
	}

	private boolean isStartDateEqualsToDefaultValue(GetStatsInput input) {
		Project group = getProject(input);
		return group.getStartDate().getTime().equals(input.getStartDate());
	}

	private int computeDefaultMonths(Date startDate) {
		Calendar today = new GregorianCalendar();
        today.setTime(new Date());
        Calendar startDateAsCalendar = new GregorianCalendar();
        startDateAsCalendar.setTime(startDate);
        return
        	((today.get(Calendar.YEAR) - startDateAsCalendar.get(Calendar.YEAR)) *12 ) +
        	today.get(Calendar.MONTH) - startDateAsCalendar.get(Calendar.MONTH);
	}

	private Project getProject(GetStatsInput input) {
		for (Project project : allProjects) {
			if (project.getName().equals(input.getGroupId()) && containsArtifactNames(project, input.getArtifactId())) {
				return project;
			}
		}
		logger.error("Could not retrieve group for input values '{}' - '{}'", input.getGroupId(), input.getArtifactId());
		return null;
	}

	private Project getProject(String projectId) {
		return getProject(this.allProjects, projectId);
	}

	private Project getProject(Collection<Project> projects, String projectId) {
		for (Project project : projects) {
			if (project.getName().equals(projectId)) {
				return project;
			}
		}
		return null;
	}

	public Artifact getArtifactForName(Project project, String id) {
		return get(project, Project.Artifact::getName, Arrays.asList(id)).stream().findFirst().orElseGet(() -> null);
	}

	public Collection<Artifact> getArtifactForAliases(Project project, Collection<String> aliases) {
		return get(project, Project.Artifact::getAlias, aliases);
	}

	private boolean containsArtifactNames(Project project, String... artifactIds) {
		return containsArtifactNames(project, Arrays.asList(artifactIds));
	}

	private boolean containsArtifactNames(Project project, Collection<String> artifactIds) {
		return !get(project, Project.Artifact::getName, artifactIds).isEmpty();
	}

	private Collection<Project.Artifact> get(Project project, Function<Artifact, String> propertySupplier, Collection<String> values) {
		return project.getArtifacts().stream().filter(artifact ->  values.contains(propertySupplier.apply(artifact))).collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private String getKey(GetStatsInput input) {
		return
			input.getClass().getName() + ";" +
			input.getProjectId() + ";" +
			input.getGroupId() + ";" +
			input.getArtifactId() + ";" +
			new SimpleDateFormat("yyyyMM").format(input.getStartDate()) + ";" +
			(isMonthsEqualsToDefaultValue(input) ?
				"diffFromToday":
				input.getMonths());
	}

	public GetLatestVersionOutput getLatestRelease(String groupId, String artifactId) {
		String key = groupId + ":" + artifactId + ".latestRelease";
		GetLatestVersionOutput output = (GetLatestVersionOutput)inMemoryCache.get(key);
		if (output == null) {
			output = cache.load(key);
			if (output != null) {
				inMemoryCache.put(key, output);
			}
		}
		if (output != null) {
			if ((new Date().getTime() - output.getTime().getTime()) <= 600000) {
    			return output;
    		}
		}
		GetLatestVersionOutput oldOutput = output;
		return Synchronizer.execute(Objects.getId(this) + key, () -> {
			GetLatestVersionOutput newOutput;
			try {
				newOutput = callGetLatestRelease(groupId, artifactId);
			} catch (Throwable exc) {
				if (oldOutput != null) {
					return oldOutput;
				}
				return Throwables.rethrow(exc);
			}
    		newOutput.setTime(new Date());
    		cache.store(key, newOutput);
			inMemoryCache.put(key, newOutput);
			return newOutput;
		});
	}

	public GetLatestVersionOutput callGetLatestRelease(String groupId, String artifactId) {
		UriComponents uriComponents =
			getStatsUriComponentsBuilder.get()
			.path("/service/local/lucene/search")
			.queryParam("g", groupId)
			.queryParam("a", artifactId)
			.queryParam("collapseresults", "true")
			.build();
		ResponseEntity<String> response = restTemplate.exchange(
			uriComponents.toString(),
			HttpMethod.GET,
			entity,
			String.class
		);
		String responseBody = response.getBody();
		if (responseBody != null) {
			Matcher latestReleaseFinder = latestReleasePattern.matcher(responseBody);
			if (latestReleaseFinder.find()) {
				GetLatestVersionOutput output = new GetLatestVersionOutput();
				output.setValue(latestReleaseFinder.group(1));
				return output;
			}
		}
		return new GetLatestVersionOutput();
	}

	private GetArtifactListOutput callGetArtifactListRemote(GetStatsInput input) throws JAXBException {
		UriComponents uriComponents =
			getStatsUriComponentsBuilder.get()
			.path("/service/local/stats/coord")
			.pathSegment(input.getGroupId())
			.queryParam("g", input.getProjectId())
			.build();
		ResponseEntity<String> response = restTemplate.exchange(
			uriComponents.toString(),
			HttpMethod.GET,
			entity,
			String.class
		);
		GetArtifactListOutput output = (GetArtifactListOutput) jaxbContext.createUnmarshaller().unmarshal(new StringReader(response.getBody()));
		return output;
	}

	private GetGroupListOutput callGetGroupListRemote() throws JAXBException {
		UriComponents uriComponents =
			getStatsUriComponentsBuilder.get()
			.path("/service/local/stats/projects")
			.build();
		ResponseEntity<String> response = restTemplate.exchange(
			uriComponents.toString(),
			HttpMethod.GET,
			entity,
			String.class
		);
		GetGroupListOutput output = (GetGroupListOutput) jaxbContext.createUnmarshaller().unmarshal(new StringReader(response.getBody()));
		return output;
	}

	private GetStatsOutput callGetStatsRemote(GetStatsInput input) throws JAXBException {
		UriComponents uriComponents =
			getStatsUriComponentsBuilder.get()
			.path("/service/local/stats/timeline")
			.queryParam("t", "raw")
			.queryParam("g", input.getGroupId())
			.queryParam("p", input.getProjectId())
			.queryParamIfPresent("a", Optional.ofNullable(input.getArtifactId()))
			.queryParam("from", new SimpleDateFormat("yyyyMM").format(input.getStartDate()))
			.queryParam("nom", input.getMonths())
			.build();
		ResponseEntity<String> response = restTemplate.exchange(
			uriComponents.toString(),
			HttpMethod.GET,
			entity,
			String.class
		);
		GetStatsOutput output = (GetStatsOutput) jaxbContext.createUnmarshaller().unmarshal(new StringReader(response.getBody()));
		return output;
	}

	@lombok.NoArgsConstructor
	@lombok.Getter
	@lombok.Setter
	@lombok.ToString
	@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
	public static class Configuration implements Serializable {

		private static final long serialVersionUID = 1184292213929598477L;

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM")
		private Calendar startDate;
		private String host;
		private String scheme;
		private Boolean enabled;
		private Cache cache;
		private Authorization authorization;

		private Collection<Project> project;

		@lombok.NoArgsConstructor
		@lombok.Getter
		@lombok.Setter
		@lombok.ToString
		@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
		public static class Authorization implements Serializable {

			private static final long serialVersionUID = -782745729613213518L;

			private Token token;

			@lombok.NoArgsConstructor
			@lombok.Getter
			@lombok.Setter
			@lombok.ToString
			@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
			public static class Token implements Serializable {

				private static final long serialVersionUID = -3121176453599817634L;

				private String type;
				private String value;
			}
		}

		@lombok.NoArgsConstructor
		@lombok.Getter
		@lombok.Setter
		@lombok.ToString
		public static class Cache implements Serializable {

			private static final long serialVersionUID = -5248107701847556177L;

			private Long ttl;
			private Integer dayOfTheMonthFromWhichToLeave;


		}
	}

	@XmlRootElement(name = "statsProjectListResp")
	@XmlAccessorType(XmlAccessType.FIELD)
	@lombok.NoArgsConstructor
	@lombok.Getter
	@lombok.Setter
	@lombok.ToString
	public static class GetGroupListOutput implements Serializable {

		private static final long serialVersionUID = 818135083673471322L;

		@XmlElement
		private Data data;

		@XmlAccessorType(XmlAccessType.FIELD)
		@XmlType(namespace = "GetGroupListOutput.Data")
		@lombok.NoArgsConstructor
		@lombok.Getter
		@lombok.Setter
		@lombok.ToString
		public static class Data implements Serializable {

			private static final long serialVersionUID = 1845567476647298831L;

			@XmlElement(name = "statsProject")
			private List<Group> groups;

			@XmlAccessorType(XmlAccessType.FIELD)
			@lombok.NoArgsConstructor
			@lombok.Getter
			@lombok.Setter
			@lombok.ToString
			public static class Group implements Serializable {

				private static final long serialVersionUID = 2507704512441988141L;

				private String id;
				private String name;

			}
		}

	}

	@XmlRootElement(name = "statsCoordResp")
	@XmlAccessorType(XmlAccessType.FIELD)
	@lombok.NoArgsConstructor
	@lombok.Getter
	@lombok.Setter
	@lombok.ToString
	public static class GetArtifactListOutput implements Serializable {

		private static final long serialVersionUID = 8251590612495213606L;

		@XmlElement
		private Data data;

		@XmlAccessorType(XmlAccessType.FIELD)
		@XmlType(namespace = "GetArtifactListOutput.Data")
		@lombok.NoArgsConstructor
		@lombok.Getter
		@lombok.Setter
		@lombok.ToString
		public static class Data implements Serializable {

			private static final long serialVersionUID = 159613227785489872L;

			@XmlElement(name = "coord")
			private List<String> artifacts;

		}

	}

	@lombok.NoArgsConstructor
	@lombok.AllArgsConstructor
	@lombok.Getter
	@lombok.Setter
	@lombok.ToString
    public static class GetStatsInput {

    	private String projectId;
    	private String groupId;
    	private String artifactId;
    	private Date startDate;
    	private Integer months;

    }

    @lombok.NoArgsConstructor
    @lombok.Getter
    @lombok.Setter
    @lombok.ToString
    public static class GetAllStatsOutput implements Serializable {

		private static final long serialVersionUID = 287571224336835644L;

		private Long totalDownloads;
    	private List<Integer> downloadsForMonth;

    }

	@XmlRootElement(name = "statsTimelineResp")
	@XmlAccessorType(XmlAccessType.FIELD)
	@lombok.NoArgsConstructor
	@lombok.Getter
	@lombok.Setter
	@lombok.ToString
	public static class GetStatsOutput implements Serializable {

		private static final long serialVersionUID = 6761217401866880541L;

		@XmlTransient
		private Date time;

		@XmlElement
		private Data data;

		@XmlAccessorType(XmlAccessType.FIELD)
		@XmlType(namespace = "GetStatsOutput.Data")
		@lombok.NoArgsConstructor
		@lombok.Getter
		@lombok.Setter
		@lombok.ToString
		public static class Data implements Serializable {

			private static final long serialVersionUID = -5929067539263340061L;

			@XmlElement
			private String projectId;

			@XmlElement
			private String groupId;

			@XmlElement
			private String artifactId;

			@XmlElement
			private String type;

			@XmlElement
			private long total;

			@XmlElement
			private Timeline timeline;

			@XmlAccessorType(XmlAccessType.FIELD)
			@lombok.NoArgsConstructor
			@lombok.Getter
			@lombok.Setter
			@lombok.ToString
			public static class Timeline implements Serializable {

				private static final long serialVersionUID = 2507704512441988141L;

				@XmlElement(name = "int")
				private List<Integer> values;

			}
		}

	}

	@lombok.NoArgsConstructor
	@lombok.Getter
	@lombok.Setter
	@lombok.ToString
	public static class GetLatestVersionOutput implements Serializable {

		private static final long serialVersionUID = 6761217401866880541L;

		private Date time;
		private String value;

	}

	public static class Group {
		private Collection<NexusConnector> nexusConnectors;
		private Configuration configuration;

		public Group(SimpleCache cache, RestTemplate restTemplate, Utility utility, Map<String, Object> configMap) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException, JAXBException, ParseException, IOException {
			ObjectMapper mapper = new ObjectMapper();
			Configuration configuration = mapper.readValue(
				this.getClass().getClassLoader().getResourceAsStream("nexus-connector.group.config.default.json"),
				Configuration.class
			);
			Configuration customConfiguration = mapper.readValue(
				(String)configMap.get("config"),
				Configuration.class
			);
			Project customDefaultProjectConfig = customConfiguration.getDefaultProjectConfig();
			if (customDefaultProjectConfig != null && customDefaultProjectConfig.getStartDate() != null) {
				configuration.getDefaultProjectConfig().setStartDate(customDefaultProjectConfig.getStartDate());
			}
			Iterator<NexusConnector.Configuration> defaultNexusConnectorConfigItr = configuration.getConnector().iterator();
			NexusConnector.Configuration defaultNexusConnectorConfig = defaultNexusConnectorConfigItr.next();
			defaultNexusConnectorConfigItr.remove();
			for (NexusConnector.Configuration nexusConnectorConfig : customConfiguration.getConnector()) {
				NexusConnector.Configuration.Authorization.Token token = nexusConnectorConfig.getAuthorization().getToken();
				if (token.getType() == null) {
					token.setType(defaultNexusConnectorConfig.getAuthorization().getToken().getType());
				}
				NexusConnector.Configuration.Cache cacheConfig = nexusConnectorConfig.getCache();
				if (cacheConfig == null) {
					nexusConnectorConfig.setCache(defaultNexusConnectorConfig.getCache());
				} else {
					if (cacheConfig.getDayOfTheMonthFromWhichToLeave() == null) {
						cacheConfig.setDayOfTheMonthFromWhichToLeave(defaultNexusConnectorConfig.getCache().getDayOfTheMonthFromWhichToLeave());
					}
					//if (cacheConfig.getTtl() == null) {
						cacheConfig.setTtl(defaultNexusConnectorConfig.getCache().getTtl());
					//}
				}
				if (nexusConnectorConfig.getHost() == null) {
					nexusConnectorConfig.setHost(defaultNexusConnectorConfig.getHost());
				}
				if (nexusConnectorConfig.getScheme() == null) {
					nexusConnectorConfig.setScheme(defaultNexusConnectorConfig.getScheme());
				}
				if (nexusConnectorConfig.getEnabled() == null) {
					nexusConnectorConfig.setEnabled(defaultNexusConnectorConfig.getEnabled());
				}
				configuration.getConnector().add(nexusConnectorConfig);
			}
			nexusConnectors = ConcurrentHashMap.newKeySet();
			this.configuration = configuration;
			for (org.burningwave.services.NexusConnector.Configuration nexusConfiguration : configuration.getConnector()) {
				if (!nexusConfiguration.getEnabled()) {
					continue;
				}
				nexusConfiguration.setStartDate(configuration.getDefaultProjectConfig().getStartDate());
				NexusConnector nexusConnector = new NexusConnector(restTemplate, cache, utility, nexusConfiguration);
				nexusConnectors.add(nexusConnector);
			}
		}

		public GetLatestVersionOutput getLatestRelease(String artifactId) {
			String[] artifactIdAsSplittedString = artifactId.split(":");
			if (artifactIdAsSplittedString.length != 2) {
				throw new IllegalArgumentException("artifactId must be in the form 'groupId:artifactId' ('" + artifactId + "' provided)");
			}
			for (NexusConnector nexusConnector : nexusConnectors) {
				for (Project project : nexusConnector.allProjects) {
					if (project.getName().equals(artifactIdAsSplittedString[0]) &&
						nexusConnector.containsArtifactNames(project, artifactIdAsSplittedString[1])
					) {
						return nexusConnector.getLatestRelease(
							project.getName(),
							artifactIdAsSplittedString[1]
						);
					}
				}
			}
			return null;
		}

		public GetAllStatsOutput getAllStats(Set<String> groupIds, Set<String> aliases, Set<String> artifactIds, Date startDate, Integer months)
			throws ParseException, JAXBException, InterruptedException, ExecutionException
		{
			Collection<CompletableFuture<GetStatsOutput>> outputSuppliers = new ArrayList<>();
			for (NexusConnector nexusConnector : nexusConnectors) {
				Set<String> artifactsToBeLoaded = new LinkedHashSet<>();
				for (Project project : nexusConnector.allProjects) {
					if (groupIds != null && !groupIds.contains(project.getName())) {
						continue;
					}
					if (artifactIds == null && aliases == null) {
						for (Project.Artifact artifact : project.getArtifacts()) {
							artifactsToBeLoaded.add(project.getName() + ":" + artifact.getName());
						}
						continue;
					}
					if (artifactIds != null) {
						for (String artifactId : artifactIds) {
							String[] artifactIdAsSplittedString = artifactId.split(":");
							if (artifactIdAsSplittedString.length > 1) {
								if (project.getName().equals(artifactIdAsSplittedString[0]) &&
									nexusConnector.containsArtifactNames(project, artifactIdAsSplittedString[1])
								) {
									artifactsToBeLoaded.add(project.getName() + ":" + artifactIdAsSplittedString[1]);
								}
							} else if (nexusConnector.containsArtifactNames(project, artifactIdAsSplittedString[0])) {
								artifactsToBeLoaded.add(project.getName() + ":" + artifactIdAsSplittedString[0]);
							}
						}
					}
					if (aliases != null) {
						Collection<Project.Artifact> artifacts = nexusConnector.getArtifactForAliases(project, aliases);
						for (Project.Artifact artifact : artifacts) {
							artifactsToBeLoaded.add(project.getName() + ":" + artifact.getName());
						}
					}
				}
				for (String projectAndArtifactId : artifactsToBeLoaded) {
					outputSuppliers.add(CompletableFuture.supplyAsync(() ->
						nexusConnector.getStats(
							toInput(nexusConnector, nexusConnector.getProject(projectAndArtifactId.split(":")[0]), projectAndArtifactId.split(":")[1], startDate, months)
						)
					));
				}
			}
			GetAllStatsOutput output = merge(outputSuppliers.stream().map(outputSupplier -> outputSupplier.join()).collect(Collectors.toList()));
			if (output == null) {
				throw new IllegalArgumentException("No items found for group with id '" + groupIds + "' and for artifact with id '" + artifactIds + "'" + "' and for artifact with alias '" + aliases + "'");
			}
			return output;
		}

		Configuration getConfiguration() {
			return configuration;
		}

		private GetStatsInput toInput(NexusConnector nexusConnector, Project projectInfo, String artifactId, Date startDate, Integer months) {
			startDate = startDate != null ? startDate : projectInfo.getStartDate().getTime();
			return new GetStatsInput(
				projectInfo.getId(),
				projectInfo.getName(),
				artifactId,
				startDate,
				months != null ? months : nexusConnector.computeDefaultMonths(startDate)
			);
		}

		private GetAllStatsOutput merge(Collection<GetStatsOutput> getStatsOutputs) {
			if (getStatsOutputs != null && getStatsOutputs.size() > 0) {
				GetAllStatsOutput output = new GetAllStatsOutput();
				for (GetStatsOutput getStatsOutput : getStatsOutputs) {
					sum(output, getStatsOutput);
				}
				return cleanUp(output);
			}
			return null;
		}

		private GetAllStatsOutput cleanUp(GetAllStatsOutput output) {
			List<Integer> downloadsForMonth = output.getDownloadsForMonth();
			for (int i = 0; i <  downloadsForMonth.size(); i++) {
				if (downloadsForMonth.get(i) == 0) {
					downloadsForMonth.set(i, null);
				} else {
					break;
				}
			}
			return output;
		}

		private void sum(GetAllStatsOutput output, GetStatsOutput getStatsOutput) {
			if (output.getTotalDownloads() == null) {
				output.setTotalDownloads(getStatsOutput.getData().getTotal());
				output.setDownloadsForMonth(new ArrayList<>(getStatsOutput.getData().getTimeline().getValues()));
				return;
			}
			output.setTotalDownloads(getStatsOutput.getData().getTotal() + output.getTotalDownloads());
			List<Integer> outputValues = output.getDownloadsForMonth();
			List<Integer> getStatsOutputValues = getStatsOutput.getData().getTimeline().getValues();
			for (int i = 0; i < outputValues.size(); i++) {
				outputValues.set(i, outputValues.get(i) + (i < getStatsOutputValues.size() ? getStatsOutputValues.get(i) : 0));
			}
		}

		public void clearCache() {
			for (NexusConnector nexusConnector : nexusConnectors) {
				nexusConnector.clearCache();
			}
		}

		public Collection<String[]> getAllProjectInfos() {
			Map<String, String[]> projectInfos = new TreeMap<>();
			for (NexusConnector nexusConnector : nexusConnectors) {
				for (String[] projectInfo : nexusConnector.getAllProjectInfos()) {
					projectInfos.put(projectInfo[0], projectInfo);
				}

			}
			return projectInfos.values();
		}


		@lombok.NoArgsConstructor
		@lombok.Getter
		@lombok.Setter
		@lombok.ToString
		@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
		public static class Configuration implements Serializable {

			private static final long serialVersionUID = -6792668118990450187L;

			private Project defaultProjectConfig;
			private Collection<NexusConnector.Configuration> connector;

		}

	}

	@lombok.NoArgsConstructor
	@lombok.Getter
	@lombok.Setter
	@lombok.ToString
	@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
	public static class Project implements Serializable {

		private static final long serialVersionUID = -5218467892927341444L;

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM")
		private Calendar startDate;
		private String id;
		private String name;
		private Collection <Artifact> artifacts;


		@lombok.NoArgsConstructor
		@lombok.Getter
		@lombok.Setter
		@lombok.ToString
		@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
		public static class Artifact implements Serializable {

			private static final long serialVersionUID = -222016209791534123L;

			private String name;
			private String alias;
			private String color;
			private String site;

		}
	}

}