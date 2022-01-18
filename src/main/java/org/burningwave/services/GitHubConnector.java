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
import java.util.Map.Entry;
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

public class GitHubConnector {

	private final static org.slf4j.Logger logger;

	private RestTemplate restTemplate;
	private HttpEntity<String> entity;
	private Supplier<UriComponentsBuilder> getStarCountUriComponentsBuilder;
	private Map<String, Collection<String>> allProjectsInfo;
	private Map<String, GetStarCountOutput> inMemoryCache;
	private long timeToLiveForInMemoryCache;

	@Autowired
    private SimpleCache cache;


    static {
    	logger = org.slf4j.LoggerFactory.getLogger(GitHubConnector.class);
    }

    public GitHubConnector(Map<String, Object> configMap) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
    	restTemplate = new RestTemplate();
    	HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", configMap.get("authorization.token.type") + " " + configMap.get("authorization.token"));
        entity = new HttpEntity<String>(headers);
        getStarCountUriComponentsBuilder = () -> UriComponentsBuilder.newInstance()
        	.scheme("https")
        	.host((String)configMap
        	.get("host"))
        	.pathSegment("repos");
        allProjectsInfo = retrieveProjectsInfo((String)configMap.get("projects-info"));
        inMemoryCache = new ConcurrentHashMap<>();
        timeToLiveForInMemoryCache = Long.parseLong((String)configMap.get("cache.ttl"));
    }


	@SuppressWarnings("rawtypes")
	private GetStarCountOutput callRemoteService(Input input) {
		UriComponents uriComponents =
			getStarCountUriComponentsBuilder.get().pathSegment(input.getUsername()).pathSegment(input.getRepositoryName())
			.build();
		ResponseEntity<Map> response = restTemplate.exchange(
			uriComponents.toString(),
			HttpMethod.GET,
			entity,
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

    private Map<String, Collection<String>> retrieveProjectsInfo(String projectInfosAsString) {
    	Map<String, Collection<String>> projectsInfo = new ConcurrentHashMap<>();
    	for (String projectInfoAsString : projectInfosAsString.split(";")) {
    		Collection<String> repoNames = new CopyOnWriteArrayList<>();
    		String[] infos = projectInfoAsString.split("/");
    		projectsInfo.put(infos[0], repoNames);
    		for (int i = 1; i < infos.length; i++) {
    			repoNames.add(infos[i]);
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
				logger.info("Object with id '{}' loaded from physical cache", key);
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
				newOutput = callRemoteService(input);
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
			input.getRepositoryName();
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
			input.setRepositoryName(repositoryName);
			outputSuppliers.add(
				CompletableFuture.supplyAsync(() ->
					function.apply(input)
				)
			);
		} else if (username == null && repositoryName != null) {
			for (Entry<String, Collection<String>> usernameAndRepositories : allProjectsInfo.entrySet()) {
				if (usernameAndRepositories.getValue().contains(repositoryName)) {
					Input input = new Input();
					input.setUsername(usernameAndRepositories.getKey());
					input.setRepositoryName(repositoryName);
					outputSuppliers.add(
						CompletableFuture.supplyAsync(() ->
						function.apply(input)
						)
					);
				}
			}
		} else if (username != null && repositoryName == null) {
			for (String repoName : allProjectsInfo.get(username)) {
				Input input = new Input();
				input.setUsername(username);
				input.setRepositoryName(repoName);
				outputSuppliers.add(
					CompletableFuture.supplyAsync(() ->
						function.apply(input)
					)
				);
			}
		} else {
			for (Entry<String, Collection<String>> usernameAndRepositories : allProjectsInfo.entrySet()) {
				for (String repoName : usernameAndRepositories.getValue()) {
					Input input = new Input();
					input.setUsername(usernameAndRepositories.getKey());
					input.setRepositoryName(repoName);
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

	public static class Input {
		private String username;
		private String repositoyName;

		public String getUsername() {
			return username;
		}
		public void setUsername(String user) {
			this.username = user;
		}
		public String getRepositoryName() {
			return repositoyName;
		}
		public void setRepositoryName(String repoName) {
			this.repositoyName = repoName;
		}


	}

	public static class GetStarCountOutput implements Serializable {

		private static final long serialVersionUID = 5045091698760296866L;

		private Date time;
		private Integer count;


		public Date getTime() {
			return time;
		}
		public void setTime(Date time) {
			this.time = time;
		}
		public Integer getCount() {
			return count;
		}
		public void setCount(Integer count) {
			this.count = count;
		}


	}

}
