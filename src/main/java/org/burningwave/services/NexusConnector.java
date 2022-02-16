package org.burningwave.services;

import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.io.Serializable;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

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
	private Collection<Project> allGroupsInfo;
	private Map<String, GetStatsOutput> inMemoryCache;
	private long timeToLiveForInMemoryCache;
	private int dayOfTheMonthFromWhichToLeave;
    private SimpleCache cache;
    private Utility utility;


    static {
    	logger = org.slf4j.LoggerFactory.getLogger(NexusConnector.class);
    }

    public NexusConnector(Utility utility, Map<String, Object> configMap) throws JAXBException, ParseException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
    	this.utility = utility;
    	restTemplate = new RestTemplate();
    	HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", configMap.get("authorization.token.type") + " " + configMap.get("authorization.token"));
        entity = new HttpEntity<String>(headers);
        jaxbContext = JAXBContext.newInstance(GetStatsOutput.class);
        getStatsUriComponentsBuilder = () -> UriComponentsBuilder.newInstance().scheme("https").host((String)configMap.get("host")).path("/service/local/stats/timeline").queryParam("t", "raw");
        allGroupsInfo = retrieveGroupsInfo(configMap);
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
		for (Project project : allGroupsInfo) {
			for (Map.Entry<String, Artifact> artifactEntry : project.getArtifacts().entrySet()) {
				projectInfos.add(new String[]{project.getGroupName() + ":" + artifactEntry.getKey(), artifactEntry.getValue().getColor()});
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
		Project group = getGroup(input);
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

	private Project getGroup(GetStatsInput input) {
		for (Project groupInfo : allGroupsInfo) {
			if (groupInfo.getGroupName().equals(input.getGroupId()) && groupInfo.getArtifacts().values().stream().filter(artifact -> artifact.getId().equals(input.getArtifactId())).findFirst().isPresent()) {
				return groupInfo;
			}
		}
		logger.error("Could not retrieve group for input values '{}' - '{}'", input.getGroupId(), input.getArtifactId());
		return null;
	}

	private Collection<Project> retrieveGroupsInfo(Map<String, Object> configMap) throws ParseException {
        String projectsInfoAsString = (String)configMap.get("projects-info");
        String[] projectsInfoAsSplittedString = projectsInfoAsString.split(";");
        Calendar startDateAsCalendar = new GregorianCalendar();
        startDateAsCalendar.setTime(new SimpleDateFormat("yyyy-MM").parse((String)configMap.get("projects-info.start-date")));
        Collection<Project> projectsInfo = new ArrayList<>();
        for (int i = 0; i < projectsInfoAsSplittedString.length; i++) {
        	Project project = new Project();
        	project.setStartDate(startDateAsCalendar);
        	String[] projectInfoAsSplittedString = projectsInfoAsSplittedString[i].split("/");
        	project.setGroupId(projectInfoAsSplittedString[0]);
        	project.setGroupName(projectInfoAsSplittedString[1]);
        	Map<String, Project.Artifact> articactIds = new HashMap<>();
        	project.setArtifacts(articactIds);
        	for (int j = 2; j < projectInfoAsSplittedString.length; j++) {
        		String[] projectAndColorAsSplittedString = projectInfoAsSplittedString[j].split("\\\\");
        		Project.Artifact artifact = new Project.Artifact();
        		if (projectAndColorAsSplittedString.length > 1) {
        			artifact.setColor(projectAndColorAsSplittedString[1]);
        		} else {
        			artifact.setColor(utility.randomHex());
        		}
        		String[] projectNames = projectAndColorAsSplittedString[0].split(":");
        		if (projectNames.length == 1) {
        			artifact.setId(projectNames[0]);
        		} else if (projectNames.length == 2) {
        			artifact.setId(projectNames[1]);
        		} else {
        			throw new IllegalArgumentException();
        		}
        		articactIds.put(projectNames[0], artifact);
        	}
        	projectsInfo.add(project);
        }
        return projectsInfo;
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

	private GetStatsOutput callGetStatsRemote(GetStatsInput input) throws JAXBException {
		UriComponents uriComponents =
			getStatsUriComponentsBuilder.get().queryParam("p", input.getProjectId())
			.queryParam("g", input.getGroupId())
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
		private static final long serialVersionUID = 3642117423686944572L;

		@XmlTransient
		private Date time;

		@XmlElement
		private Data data;

		@XmlAccessorType(XmlAccessType.FIELD)
		@NoArgsConstructor
		@Getter
		@Setter
		@ToString
		public static class Data implements Serializable {

			private static final long serialVersionUID = -5272099947960951863L;

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

		public Group(SimpleCache cache, Utility utility, Map<String, Object> configMap) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException, JAXBException, ParseException {
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
				nexusConfiguration.put("projects-info.start-date", startDate);
				NexusConnector nexusConnector = new NexusConnector(utility, nexusConfiguration);
				nexusConnector.cache = cache;
				nexusConnectors.add(nexusConnector);
			}
		}

		public GetAllStatsOutput getAllStats(Set<String> groupIds, String artifactId, Date startDate, Integer months)
				throws ParseException, JAXBException, InterruptedException, ExecutionException {
			Collection<CompletableFuture<GetStatsOutput>> outputSuppliers = new ArrayList<>();
			for (NexusConnector nexusConnector : nexusConnectors) {
				for (Project projectInfo : nexusConnector.allGroupsInfo) {
					if (groupIds != null && !groupIds.contains(projectInfo.getGroupName())) {
						continue;
					}
					if (artifactId == null) {
						for (Map.Entry<String, Project.Artifact> artifactEntry : projectInfo.getArtifacts().entrySet()) {
							outputSuppliers.add(CompletableFuture.supplyAsync(() ->
								nexusConnector.getStats(
									toInput(nexusConnector, projectInfo, artifactEntry.getKey(), startDate, months)
								)
							));
						}
					} else if (projectInfo.getArtifacts().containsKey(artifactId)) {
						outputSuppliers.add(CompletableFuture.supplyAsync(() ->
							nexusConnector.getStats(
								toInput(nexusConnector, projectInfo, artifactId, startDate, months)
							)
						));
					} else {
						logger.warn("artifactId '{}' not found under groupId '{}'", artifactId, projectInfo.getGroupName());
						continue;
					}
				}
			}
			GetAllStatsOutput output = merge(outputSuppliers.stream().map(outputSupplier -> outputSupplier.join()).collect(Collectors.toList()));
			if (output == null) {
				throw new IllegalArgumentException("No items found for Group with id '" + groupIds + "' and for artifact with id '" + artifactId + "'");
			}
			return output;
		}


		private GetStatsInput toInput(NexusConnector nexusConnector, Project projectInfo, String artifactId, Date startDate, Integer months) {
			startDate = startDate != null ? startDate : projectInfo.getStartDate().getTime();
			return new GetStatsInput(
				projectInfo.getGroupId(),
				projectInfo.getGroupName(),
				artifactId != null? projectInfo.getArtifacts().get(artifactId).getId() : null,
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

		public Object getAllProjectInfos() {
			Collection<String[]> projectInfos = new ArrayList<>();
			for (NexusConnector nexusConnector : nexusConnectors) {
				projectInfos.addAll(nexusConnector.getAllProjectInfos());
			}
			return projectInfos;
		}
	}



	static class Project {

		private Calendar startDate;
		private String groupId;
		private String groupName;
		private Map<String, Artifact> artifacts;


		public Calendar getStartDate() {
			return startDate;
		}
		public void setStartDate(Calendar startDate) {
			this.startDate = startDate;
		}


		public String getGroupId() {
			return groupId;
		}
		public void setGroupId(String groupId) {
			this.groupId = groupId;
		}


		public String getGroupName() {
			return groupName;
		}
		public void setGroupName(String groupName) {
			this.groupName = groupName;
		}


		public Map<String, Artifact> getArtifacts() {
			return artifacts;
		}
		public void setArtifacts(Map<String, Artifact> artifacts) {
			this.artifacts = artifacts;
		}



		@Override
		public String toString() {
			return "Project [startDate=" + startDate + ", groupId=" + groupId + ", groupName=" + groupName
					+ ", artifacts=" + artifacts + "]";
		}


		static class Artifact {
			private String id;
			private String color;

			public String getId() {
				return id;
			}
			public void setId(String id) {
				this.id = id;
			}
			public String getColor() {
				return color;
			}
			public void setColor(String color) {
				this.color = color;
			}

			@Override
			public String toString() {
				return "Artifact [id=" + id + ", color=" + color + "]";
			}

		}
	}

}