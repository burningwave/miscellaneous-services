package org.burningwave.services;

import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.io.Serializable;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

public class NexusConnector {
	private final static org.slf4j.Logger logger;

	private RestTemplate restTemplate;
	private HttpEntity<String> entity;
	private JAXBContext jaxbContext;
	private Supplier<UriComponentsBuilder> getStatsUriComponentsBuilder;
	private Collection<Project> allProjects;
	private Map<String, GetStatsOutput> inMemoryCache;
	private long timeToLiveForInMemoryCache;
	private int dayOfTheMonthFromWhichToLeave;
    private SimpleCache cache;
    private Utility utility;


    static {
    	logger = org.slf4j.LoggerFactory.getLogger(NexusConnector.class);
    }

    public NexusConnector(Utility utility, Map<String, Object> configMap) throws JAXBException, ParseException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException, JsonMappingException, JsonProcessingException {
    	this.utility = utility;
    	restTemplate = new RestTemplate();
    	HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", configMap.get("authorization.token.type") + " " + configMap.get("authorization.token"));
        entity = new HttpEntity<String>(headers);
        jaxbContext = JAXBContext.newInstance(GetGroupListOutput.class, GetArtifactListOutput.class, GetStatsOutput.class);
        getStatsUriComponentsBuilder = () -> UriComponentsBuilder.newInstance().scheme((String)configMap.get("scheme")).host((String)configMap.get("host"));
        allProjects = retrieveProjectInfos(configMap);
        logger.info("Projects configuration: {}", allProjects);
        inMemoryCache = new ConcurrentHashMap<>();
        timeToLiveForInMemoryCache = Long.parseLong((String)configMap.get("cache.ttl"));
        dayOfTheMonthFromWhichToLeave = Integer.parseInt((String)configMap.get("cache.day-of-the-month-from-which-to-leave"));
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
		GetStatsOutput output = inMemoryCache.get(key);
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
    		if (oldOutput != null && oldOutput.getData().getTotal() == newOutput.getData().getTotal()) {
    			newDate.setTime(oldOutput.getTime());
    			newDate.add(Calendar.DATE, 1);
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

	/* nexus-connector.projects-info property must be in JSon format
	 * Examples of nexus-connector.projects-info property values:
		- [{
		    "name": "org.burningwave",
		    "artifacts": [{
		            "name": "jvm-driver",
		            "color": "00ff00",
		            "site": "https://burningwave.github.io/jvm-driver/"
		        }, {
		            "name": "core",
		            "alias": "core",
		            "color": "e54d1d",
		            "site": "https://burningwave.github.io/core/"
		        }, {
		            "name": "graph",
		            "alias": "graph",
		            "color": "f7bc12",
		            "site": "https://burningwave.github.io/graph/"
		        }, {
		            "name": "tools",
		            "color": "066da1",
		            "site": "https://burningwave.github.io/tools/"
		        }
		    ]
		}, {
		    "name": "com.github.burningwave",
		    "artifacts": [{
		            "name": "bw-core",
		            "alias": "core",
		            "color": "e54d1d",
		            "site": "https://burningwave.github.io/core/"
		        }, {
		            "name": "bw-graph",
		            "alias": "graph",
		            "color": "f7bc12",
		            "site": "https://burningwave.github.io/graph/"
		            }
		        ]
		    }
		]

		- [{
		    "name": "io.github.toolfactory",
		    "artifacts": [{
		            "name": "narcissus",
		            "color": "402a94",
		            "site": "https://toolfactory.github.io/narcissus/"
		        }, {
		            "name": "jvm-driver",
		            "color": "00ff00",
		            "site": "https://toolfactory.github.io/jvm-driver/"
		            }
		        ]
		    }
		]
	*/
	private Collection<Project> retrieveProjectInfos(Map<String, Object> configMap) throws ParseException, JAXBException, JsonMappingException, JsonProcessingException {
		GetGroupListOutput groupList = callGetGroupListRemote();
		Collection<Project> projectsInfo = new CopyOnWriteArrayList<>();
        Calendar startDateAsCalendar = new GregorianCalendar();
        startDateAsCalendar.setTime(new SimpleDateFormat("yyyy-MM").parse((String)configMap.get("projects-info.start-date")));
		for (GetGroupListOutput.Data.Group group : groupList.getData().getGroups()) {
			Project project = new Project();
			projectsInfo.add(project);
			project.setStartDate(startDateAsCalendar);
			project.setGroupId(group.getId());
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

		String projectsInfoAsString = (String)configMap.get("projects-info");
		if (projectsInfoAsString != null && !projectsInfoAsString.isEmpty()) {
			ObjectMapper mapper = new ObjectMapper();
			for (Project projectFromConfig : mapper.readValue(projectsInfoAsString, Project[].class)) {
				Project project = getProject(projectsInfo, projectFromConfig.getName());
				if (project == null) {
					throw new IllegalArgumentException("Project named " + projectFromConfig.getName() + " not found on Nexus");
				}
				for (Project.Artifact artifactFromConfig : projectFromConfig.getArtifacts()) {
					Project.Artifact artifact = getArtifactForName(project, artifactFromConfig.getName());
					if (artifact == null) {
						throw new IllegalArgumentException("Artifact named " + artifactFromConfig.getName() + " not found on Nexus");
					}
					setIfNotNull(artifact::setAlias, artifactFromConfig::getAlias);
					setIfNotNull(artifact::setColor, artifactFromConfig::getColor);
					setIfNotNull(artifact::setSite, artifactFromConfig::getSite);
				}
			}
		}
        return projectsInfo;
	}

	private <T> void setIfNotNull(Consumer<T> target, Supplier<T> source) {
		T value = source.get();
		if (value != null) {
			target.accept(value);
		}
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

	@XmlRootElement(name = "statsProjectListResp")
	@XmlAccessorType(XmlAccessType.FIELD)
	@NoArgsConstructor
	@Getter
	@Setter
	@ToString
	public static class GetGroupListOutput implements Serializable {

		private static final long serialVersionUID = 818135083673471322L;

		@XmlElement
		private Data data;

		@XmlAccessorType(XmlAccessType.FIELD)
		@XmlType(namespace = "GetGroupListOutput.Data")
		@NoArgsConstructor
		@Getter
		@Setter
		@ToString
		public static class Data implements Serializable {

			private static final long serialVersionUID = 1845567476647298831L;

			@XmlElement(name = "statsProject")
			private List<Group> groups;

			@XmlAccessorType(XmlAccessType.FIELD)
			@NoArgsConstructor
			@Getter
			@Setter
			@ToString
			public static class Group implements Serializable {

				private static final long serialVersionUID = 2507704512441988141L;

				private String id;
				private String name;

			}
		}

	}

	@XmlRootElement(name = "statsCoordResp")
	@XmlAccessorType(XmlAccessType.FIELD)
	@NoArgsConstructor
	@Getter
	@Setter
	@ToString
	public static class GetArtifactListOutput implements Serializable {

		private static final long serialVersionUID = 8251590612495213606L;

		@XmlElement
		private Data data;

		@XmlAccessorType(XmlAccessType.FIELD)
		@XmlType(namespace = "GetArtifactListOutput.Data")
		@NoArgsConstructor
		@Getter
		@Setter
		@ToString
		public static class Data implements Serializable {

			private static final long serialVersionUID = 159613227785489872L;

			@XmlElement(name = "coord")
			private List<String> artifacts;

		}

	}

	@NoArgsConstructor
	@AllArgsConstructor
	@Getter
	@Setter
	@ToString
    public static class GetStatsInput {

    	private String projectId;
    	private String groupId;
    	private String artifactId;
    	private Date startDate;
    	private Integer months;

    }

    @NoArgsConstructor
    @Getter
    @Setter
    @ToString
    public static class GetAllStatsOutput implements Serializable {

		private static final long serialVersionUID = 287571224336835644L;

		private Long totalDownloads;
    	private List<Integer> downloadsForMonth;

    }

	@XmlRootElement(name = "statsTimelineResp")
	@XmlAccessorType(XmlAccessType.FIELD)
	@NoArgsConstructor
	@Getter
	@Setter
	@ToString
	public static class GetStatsOutput implements Serializable {

		private static final long serialVersionUID = 6761217401866880541L;

		@XmlTransient
		private Date time;

		@XmlElement
		private Data data;

		@XmlAccessorType(XmlAccessType.FIELD)
		@XmlType(namespace = "GetStatsOutput.Data")
		@NoArgsConstructor
		@Getter
		@Setter
		@ToString
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
			@NoArgsConstructor
			@Getter
			@Setter
			@ToString
			public static class Timeline implements Serializable {

				private static final long serialVersionUID = 2507704512441988141L;

				@XmlElement(name = "int")
				private List<Integer> values;

			}
		}

	}

	public static class Group {
		private Collection<NexusConnector> nexusConnectors;

		public Group(SimpleCache cache, Utility utility, Map<String, Object> configMap) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException, JAXBException, ParseException, JsonMappingException, JsonProcessingException {
			configMap = new LinkedHashMap<>(configMap);
			Map<String, Map<String, Object>> allNexusConfigurations = new LinkedHashMap<>();
			String startDate = (String)configMap.remove("projects-info.start-date");
			for (Map.Entry<String, Object> confEntry : configMap.entrySet()) {
				String[] mapEntryAsStringArray;
				if (confEntry.getKey().matches("\\d{0,}\\..*?")) {
					mapEntryAsStringArray = confEntry.getKey().split("\\.", 2);
				} else {
					mapEntryAsStringArray = new String[]{"0", confEntry.getKey()};
				}
				Map<String, Object> nexusConnectionConfig = allNexusConfigurations.computeIfAbsent(mapEntryAsStringArray[0], key -> new LinkedHashMap<>());
				nexusConnectionConfig.put(mapEntryAsStringArray[1], confEntry.getValue());
			}
			nexusConnectors = ConcurrentHashMap.newKeySet();
			for (Map<String, Object> nexusConfiguration : allNexusConfigurations.values()) {
				if (!Boolean.valueOf((String)nexusConfiguration.get("enabled"))) {
					continue;
				}
				nexusConfiguration.put("projects-info.start-date", startDate);
				NexusConnector nexusConnector = new NexusConnector(utility, nexusConfiguration);
				nexusConnector.cache = cache;
				nexusConnectors.add(nexusConnector);
			}
		}

		public GetAllStatsOutput getAllStats(Set<String> groupIds, Set<String> aliases, Set<String> artifactIds, Date startDate, Integer months)
				throws ParseException, JAXBException, InterruptedException, ExecutionException {
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
				outputValues.set(i, outputValues.get(i) + getStatsOutputValues.get(i));
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
	}


	public static class Project implements Serializable {

		private static final long serialVersionUID = -5218467892927341444L;

		private Calendar startDate;
		private String id;
		private String name;
		private Collection <Artifact> artifacts;


		public Calendar getStartDate() {
			return startDate;
		}
		public void setStartDate(Calendar startDate) {
			this.startDate = startDate;
		}


		public String getId() {
			return id;
		}
		public void setGroupId(String groupId) {
			this.id = groupId;
		}


		public String getName() {
			return name;
		}
		public void setName(String groupName) {
			this.name = groupName;
		}


		public Collection<Artifact> getArtifacts() {
			return artifacts;
		}
		public void setArtifacts(Collection<Artifact> artifacts) {
			this.artifacts = artifacts;
		}

		@Override
		public String toString() {
			return "Project [startDate=" + startDate + ", id=" + id + ", name=" + name + ", artifacts=" + artifacts
					+ "]";
		}

		static class Artifact implements Serializable {

			private static final long serialVersionUID = -222016209791534123L;

			private String name;
			private String alias;
			private String color;
			private String site;

			public String getName() {
				return name;
			}
			public void setName(String value) {
				this.name = value;
			}

			public String getAlias() {
				return alias;
			}
			public void setAlias(String alias) {
				this.alias = alias;
			}

			public String getColor() {
				return color;
			}
			public void setColor(String color) {
				this.color = color;
			}

			public String getSite() {
				return site;
			}
			public void setSite(String site) {
				this.site = site;
			}


			@Override
			public String toString() {
				return "Artifact [name=" + name + ", alias=" + alias + ", color=" + color + ", site=" + site + "]";
			}

		}
	}

}