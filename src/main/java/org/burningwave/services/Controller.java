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

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;

import org.burningwave.Badge;
import org.burningwave.SimpleCache;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


@org.springframework.stereotype.Controller
@RequestMapping("/miscellaneous-services/")
@CrossOrigin
public class Controller {
	private final static org.slf4j.Logger logger;
	public final static String SWITCH_TO_REMOTE_APP_SUCCESSFUL_MESSAGE;

	private GitHubConnector gitHubConnector;
	private HerokuConnector herokuConnector;
	private NexusConnector.Group nexusConnectorGroup;
	private Badge badge;
	private Environment environment;
	private SimpleCache cache;

    static {
    	SWITCH_TO_REMOTE_APP_SUCCESSFUL_MESSAGE = "App succesfully switched";
    	logger = org.slf4j.LoggerFactory.getLogger(Controller.class);
    }

	public Controller (
		@Nullable HerokuConnector herokuConnector,
		@Nullable NexusConnector.Group nexusConnectorGroup,
		@Nullable GitHubConnector gitHubConnector,
		Badge badge,
		Environment environment,
		SimpleCache cache
	) throws InitializeException {
		this.herokuConnector = herokuConnector;
		this.nexusConnectorGroup = nexusConnectorGroup;
		this.gitHubConnector = gitHubConnector;
		this.badge = badge;
		this.cache = cache;
		this.environment = environment;
	}

    @GetMapping("/stats/artifact-download-chart")
    public String loadArtifactDownloadChart(HttpServletRequest request, Model model) {
    	return view(request, model);
    }

	@GetMapping(path = "/switch-to-remote-app")
	public String switchToRemoteApp(
		@RequestParam(value = "Authorization", required = false) String authorizationTokenAsQueryParam,
		@RequestHeader(value = "Authorization", required = false) String authorizationTokenAsHeader,
		HttpServletRequest request,
		Model model
	) throws IOException {
		String authorizationToken = authorizationTokenAsHeader != null ? authorizationTokenAsHeader : authorizationTokenAsQueryParam;
		String message;
		try {
			if ((environment.getProperty("application.authorization.token.type") + " " + environment.getProperty("application.authorization.token")).equals(authorizationToken)) {
				try {
					herokuConnector.switchToRemoteApp();
					message = SWITCH_TO_REMOTE_APP_SUCCESSFUL_MESSAGE;
				} catch (NullPointerException exc) {
					if (herokuConnector == null) {
						logger.warn(message = "Cannot switch app: the Heroku connector is disabled");
					} else {
						throw exc;
					}
				}
			} else {
				message = "Cannot switch app: unauthorized";
				logger.error(message);
			}
		} catch (Throwable exc) {
			logger.error("Exception occurred", exc);
			message = "Cannot switch app: " + exc.getMessage();
		}
		return view(request, model, message);
	}

	@GetMapping(path = "/stats/total-downloads-badge", produces = "image/svg+xml")
	public String getTotalDownloadsBadge(
		@RequestParam(value = "groupId", required = false) Set<String> groupIds,
		@RequestParam(value = "alias", required = false) Set<String> aliases,
		@RequestParam(value = "artifactId", required = false) Set<String> artifactIds,
		@RequestParam(value = "startDate", required = false) String startDate,
		@RequestParam(value = "months", required = false) String months,
		HttpServletResponse response
	) throws JAXBException, ParseException, InterruptedException, ExecutionException {
		response.setHeader("Cache-Control", "no-store");
		String label = "artifact downloads";
		return badge.build(
			getTotalDownloadsOrNull(groupIds, aliases, artifactIds, startDate, months),
			label,
			label,
			"#4c1",
			125
		);
	}

	@GetMapping(path = "/stats/star-count", produces = "application/json")
	public Integer getStarCount(
		@RequestParam(value = "repository", required = true) String[] repositories
	) {
		return getStarCountOrNull(repositories);
	}

	@GetMapping(path = "/stats/star-count-badge", produces = "image/svg+xml")
	public String getStarCountBadge(
		@RequestParam(value = "repository", required = true) String[] repositories,
		HttpServletResponse response
	) {
		response.setHeader("Cache-Control", "no-store");
		String label = "GitHub stars";
		return badge.build(
			getStarCountOrNull(repositories),
			label,
			"GitHub stars", "#78e", 93
		);
	}

	@GetMapping(path = "/clear-cache")
	public String clearCache(
		@RequestParam(value = "Authorization", required = false) String authorizationTokenAsQueryParam,
		@RequestHeader(value = "Authorization", required = false) String authorizationTokenAsHeader,
		HttpServletRequest request, Model model
	) throws IOException {
		String authorizationToken = authorizationTokenAsHeader != null ? authorizationTokenAsHeader : authorizationTokenAsQueryParam;
		Collection<String> messages = new ArrayList<>();
		String message;
		try {
			if ((environment.getProperty("application.authorization.token.type") + " " + environment.getProperty("application.authorization.token")).equals(authorizationToken)) {
				try {
					nexusConnectorGroup.clearCache();
				} catch (NullPointerException exc) {
					if (nexusConnectorGroup == null) {
						message = "The Nexus connector group is disabled";
						logger.warn(message);
						messages.add(message);
					} else {
						throw exc;
					}
				}
				try {
					gitHubConnector.clearCache();
				} catch (NullPointerException exc) {
					if (gitHubConnector == null) {
						message = "The GitHub connector is disabled";
						logger.warn(message);
						messages.add(message);
					} else {
						throw exc;
					}
				}
				cache.clear();
				if (messages.isEmpty()) {
					messages.add("Cache successfully cleaned");
				}
			} else {
				message = "Cannot clear cache: unauthorized";
				logger.warn(message);
				messages.add(message);
			}
		} catch (Throwable exc) {
			logger.error("Exception occurred", exc);
			messages.add("Exception occurred while clearing the cache: " + exc.getMessage());
		}
		return view(request, model, messages.toArray(new String[messages.size()]));
	}

	private Long getTotalDownloadsOrNull(Set<String> groupIds, Set<String> aliases, Set<String> artifactIds, String startDate, String months) {
		try {
			try {
				return nexusConnectorGroup.getAllStats(
					groupIds,
					aliases,
					artifactIds,
					startDate != null ? new SimpleDateFormat("yyyy-MM").parse(startDate) : null,
					months != null ? Integer.valueOf(months) : null
				).getTotalDownloads();
			} catch (NullPointerException exc){
				if (nexusConnectorGroup == null) {
					logger.warn("The Nexus connector group is disabled");
					return null;
				}
				throw exc;
			}
		} catch (IllegalArgumentException exc) {
			logger.error(exc.getMessage());
			return null;
		} catch (Throwable exc) {
			logger.error("Exception occurred", exc);
			return null;
		}
	}

	private Integer getStarCountOrNull(String[] repositories) {
		try {
			try {
				return gitHubConnector.getAllStarCount(repositories);
			} catch (NullPointerException exc){
				if (gitHubConnector == null) {
					logger.warn("The GitHub connector is disabled");
					return null;
				}
				throw exc;
			}
		} catch (Throwable exc) {
			logger.error("Exception occurred", exc);
			return null;
		}
	}

	private String view(HttpServletRequest request, Model model, String... message) {
		String url = request.getRequestURL().toString();
    	String basePath = url.substring(0, url.indexOf("/miscellaneous-services"));
    	model.addAttribute("basePath", basePath);
    	model.addAttribute("startDate", new SimpleDateFormat("yyyy-MM").format(nexusConnectorGroup.getConfiguration().getDefaultProjectConfig().getStartDate().getTime()));
    	if (message != null && message.length > 0) {
    		model.addAttribute("message", "[\"" + String.join("\",\"", Arrays.asList(message))  + "\"]");
    	}
        return "artifact-download-chart";
	}

}
