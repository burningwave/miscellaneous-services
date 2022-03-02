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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

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
		Environment environment,
		SimpleCache cache
	) throws InitializeException {
		if (nexusConnectorGroup == null && gitHubConnector == null) {
			throw new InitializeException("The Nexus connector group and the GitHub connector cannot be both disabled");
		}
		this.herokuConnector = herokuConnector;
		this.nexusConnectorGroup = nexusConnectorGroup;
		this.gitHubConnector = gitHubConnector;
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

	private String view(HttpServletRequest request, Model model, String... message) {
		String url = request.getRequestURL().toString();
    	String basePath = url.substring(0, url.indexOf("/miscellaneous-services"));
    	model.addAttribute("basePath", basePath);
    	if (message != null && message.length > 0) {
    		model.addAttribute("message", "[\"" + String.join("\",\"", Arrays.asList(message))  + "\"]");
    	}
        return "artifact-download-chart";
	}

}
