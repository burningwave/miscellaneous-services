package org.burningwave.services;


import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBException;

import org.burningwave.SimpleCache;
import org.burningwave.Throwables;
import org.springframework.beans.factory.annotation.Autowired;
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

public class GitHubConnector {

	private final static org.slf4j.Logger logger;

	private RestTemplate restTemplate;
	private HttpHeaders headers;
	private Supplier<UriComponentsBuilder> reposComponentsBuilder;
	private Map<String, Collection<Project>> allProjectsInfo;
	private Map<String, GetStarCountOutput> inMemoryCache;
	private long timeToLiveForInMemoryCache;

	@Autowired
    private SimpleCache cache;

    static {
    	logger = org.slf4j.LoggerFactory.getLogger(GitHubConnector.class);
    }

    public GitHubConnector(Map<String, Object> configMap) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
    	restTemplate = new RestTemplate();
    	headers = new HttpHeaders();
        headers.set("Authorization", configMap.get("authorization.token.type") + " " + configMap.get("authorization.token"));
        reposComponentsBuilder = () -> UriComponentsBuilder.newInstance()
        	.scheme("https")
        	.host((String)configMap.get("host"))
        	.pathSegment("repos");
        allProjectsInfo = retrieveProjectsInfo((String)configMap.get("projects-info"));
        inMemoryCache = new ConcurrentHashMap<>();
        timeToLiveForInMemoryCache = Long.parseLong((String)configMap.get("cache.ttl"));
    }


	@SuppressWarnings("rawtypes")
	private GetStarCountOutput callRetrieveInfoRemote(Input input) {
		UriComponents uriComponents =
			reposComponentsBuilder.get().pathSegment(input.getUsername()).pathSegment(input.getRepositoyName())
			.build();
		ResponseEntity<Map> response = restTemplate.exchange(
			uriComponents.toString(),
			HttpMethod.GET,
			new HttpEntity<String>(headers),
			Map.class
		);
		Map remoteServiceOutput = response.getBody();
		GetStarCountOutput output = new GetStarCountOutput();
		output.setCount((Integer)remoteServiceOutput.get("stargazers_count"));
		return output;
	}

	public void clearCache() {
		inMemoryCache.clear();
		logger.info("In memory cache cleaning done");
	}

    private Map<String, Collection<Project>> retrieveProjectsInfo(String projectInfosAsString) {
    	Map<String, Collection<Project>> projectsInfo = new ConcurrentHashMap<>();
    	for (String projectInfoAsString : projectInfosAsString.split(";")) {
    		Collection<Project> projects = new CopyOnWriteArrayList<>();
    		String[] infos = projectInfoAsString.split("/");
    		String[] usernameAndGroupId = infos[0].split(":");
    		projectsInfo.put(usernameAndGroupId[0], projects);
    		for (int i = 1; i < infos.length; i++) {
    			projects.add(
    				new Project(
    					infos[i]
	    			)
    			);
    		}
    	}
		return projectsInfo;
	}

	public GetStarCountOutput getStarCount(Input input) {
		String key = getKey(input);
		GetStarCountOutput output = inMemoryCache.get(key);
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
		GetStarCountOutput oldOutput = output;
		return Synchronizer.execute(Objects.getId(this) + key, () -> {
			GetStarCountOutput newOutput;
			try {
				newOutput = callRetrieveInfoRemote(input);
			} catch (Throwable exc) {
				if (oldOutput != null) {
					return oldOutput;
				}
				return Throwables.rethrow(exc);
			}
    		Calendar newDate = new GregorianCalendar();
			newDate.setTime(new Date());
			newDate.set(Calendar.HOUR_OF_DAY, 0);
			newDate.set(Calendar.MINUTE, 0);
			newDate.set(Calendar.SECOND, 0);
			newDate.set(Calendar.MILLISECOND, 0);
    		newOutput.setTime(newDate.getTime());
    		cache.store(key, newOutput);
			inMemoryCache.put(key, newOutput);
			return newOutput;
		});
    }

    private String getKey(Input input) {
    	return
			input.getClass().getName() + ";" +
			input.getUsername() + ";" +
			input.getRepositoyName();
	}


	public Integer getAllStarCount(String user, String repoName) throws JAXBException {
		return merge(invoke(this::getStarCount, user, repoName).stream().map(outputSupplier -> outputSupplier.join()).collect(Collectors.toList()));
	}

	private Integer merge(Collection<GetStarCountOutput> getStatsOutputs) {
		if (getStatsOutputs != null && getStatsOutputs.size() > 0) {
			Integer count = 0;
			for (GetStarCountOutput getStarCountOutput : getStatsOutputs) {
				count += getStarCountOutput.getCount();
			}
			return count;
		}
		return null;
	}

	private <O> Collection<CompletableFuture<O>> invoke(Function<Input, O> function, String username, String repositoryName) {
		Collection<CompletableFuture<O>> outputSuppliers = new ArrayList<>();
		if (username != null && repositoryName != null) {
			Input input = new Input();
			input.setUsername(username);
			input.setRepositoyName(repositoryName);
			outputSuppliers.add(
				CompletableFuture.supplyAsync(() ->
					function.apply(input)
				)
			);
		} else if (username == null && repositoryName != null) {
			for (Map.Entry<String, Collection<Project>> usernameAndRepositories : allProjectsInfo.entrySet()) {
				if (usernameAndRepositories.getValue().stream().filter(project -> repositoryName.equals(project.getRepositoryName())).findFirst().isPresent()) {
					Input input = new Input();
					input.setUsername(usernameAndRepositories.getKey());
					input.setRepositoyName(repositoryName);
					outputSuppliers.add(
						CompletableFuture.supplyAsync(() ->
							function.apply(input)
						)
					);
				}
			}
		} else if (username != null && repositoryName == null) {
			for (Project project : allProjectsInfo.get(username)) {
				Input input = new Input();
				input.setUsername(username);
				input.setRepositoyName(project.getRepositoryName());
				outputSuppliers.add(
					CompletableFuture.supplyAsync(() ->
						function.apply(input)
					)
				);
			}
		} else {
			for (Map.Entry<String, Collection<Project>> usernameAndRepositories : allProjectsInfo.entrySet()) {
				for (Project project : usernameAndRepositories.getValue()) {
					Input input = new Input();
					input.setUsername(usernameAndRepositories.getKey());
					input.setRepositoyName(project.getRepositoryName());
					outputSuppliers.add(
						CompletableFuture.supplyAsync(() ->
							function.apply(input)
						)
					);
				}
			}
		}
		return outputSuppliers;
	}

	@NoArgsConstructor
	@Getter
	@Setter
	public static class Input {
		private String username;
		private String repositoyName;

	}

	@NoArgsConstructor
	@Getter
	@Setter
	public static class GetStarCountOutput implements Serializable {

		private static final long serialVersionUID = 5045091698760296866L;

		private Date time;
		private Integer count;

	}


	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@ToString
	private static class Project {

		private String repositoryName;

	}
}
