package org.burningwave.services;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;

import org.burningwave.Badge;
import org.burningwave.SimpleCache;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/miscellaneous-services/")
@CrossOrigin
public class Controller {
	private final static org.slf4j.Logger logger;
	private Environment environment;
	private NexusConnector.Group nexusConnectorGroup;
	private GitHubConnector gitHubConnector;
	private SimpleCache cache;
	private Badge badge;

    static {
    	logger = org.slf4j.LoggerFactory.getLogger(Controller.class);
    }

	public Controller (
		Environment environment,
		SimpleCache cache,
		Badge badge,
		@Nullable NexusConnector.Group nexusConnectorGroup,
		@Nullable GitHubConnector gitHubConnector
	) throws InitializeException {
		if (nexusConnectorGroup == null && gitHubConnector == null) {
			throw new InitializeException("The Nexus connector group and the GitHub connector cannot be both disabled");
		}
		this.environment = environment;
		this.cache = cache;
		this.badge = badge;
		this.nexusConnectorGroup = nexusConnectorGroup;
		this.gitHubConnector = gitHubConnector;
	}

	@GetMapping(path = "/nexus-connector/project-info", produces = "application/json")
	public Object getProjectInfo() {
		try {
			try {
				return nexusConnectorGroup.getAllProjectInfos();
			} catch (NullPointerException exc){
				if (nexusConnectorGroup == null) {
					logger.warn("The Nexus connector group is disabled");
					return null;
				}
				throw exc;
			}
		} catch (Throwable exc) {
			logger.error("Exception occurred", exc);
			return "null";
		}

	}

	@GetMapping(path = "/stats/total-downloads", produces = "application/json")
	public Object getTotalDownloads(
		@RequestParam(value = "groupId", required = false) Set<String> groupIds,
		@RequestParam(value = "alias", required = false) Set<String> aliases,
		@RequestParam(value = "artifactId", required = false) Set<String> artifactIds,
		@RequestParam(value = "startDate", required = false) String startDate,
		@RequestParam(value = "months", required = false) String months
	) {
		Long value = getTotalDownloadsOrNull(groupIds, aliases, artifactIds, startDate, months);
		return value != null? value : "null";
	}

	@GetMapping(path = "/stats/downloads-for-month", produces = "application/json")
	public Object getDownloadsForMonth(
		@RequestParam(value = "groupId", required = false) Set<String> groupIds,
		@RequestParam(value = "alias", required = false) Set<String> aliases,
		@RequestParam(value = "artifactId", required = false) Set<String> artifactIds,
		@RequestParam(value = "startDate", required = false) String startDate,
		@RequestParam(value = "months", required = false) String months
	) {
		try {
			try {
				return nexusConnectorGroup.getAllStats(
					groupIds,
					aliases,
					artifactIds,
					startDate != null ? new SimpleDateFormat("yyyy-MM").parse(startDate) : null,
					months != null ? Integer.valueOf(months) : null
				).getDownloadsForMonth();
			} catch (NullPointerException exc){
				if (nexusConnectorGroup == null) {
					logger.warn("The Nexus connector group is disabled");
					return "null";
				}
				throw exc;
			}
		} catch (Throwable exc) {
			logger.error("Exception occurred", exc);
			return "null";
		}
	}

	@GetMapping(path = "/stats/total-downloads-badge", produces = "image/svg+xml")
	public Object getTotalDownloadsBadge(
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
	public Object getStarCount(
		@RequestParam(value = "username", required = false) String username,
		@RequestParam(value = "repositoryName", required = false) String repositoryName
	) {
		Integer value = getStarCountOrNull(username, repositoryName);
		return value != null? value : "null";
	}

	@GetMapping(path = "/stats/star-count-badge", produces = "image/svg+xml")
	public Object getStarCountBadge(
		@RequestParam(value = "username", required = false) String username,
		@RequestParam(value = "repositoryName", required = false) String repositoryName,
		HttpServletResponse response
	) {
		response.setHeader("Cache-Control", "no-store");
		String label = "GitHub stars";
		return badge.build(
			getStarCountOrNull(username, repositoryName),
			label,
			"GitHub stars", "#78e", 93
		);
	}

	@GetMapping(path = "/clear-cache")
	public void clearCache(
		@RequestParam(value = "Authorization", required = false) String authorizationTokenAsQueryParam,
		@RequestHeader(value = "Authorization", required = false) String authorizationTokenAsHeader,
		HttpServletRequest request,
		HttpServletResponse response
	) throws IOException {
		String authorizationToken = authorizationTokenAsHeader != null ? authorizationTokenAsHeader : authorizationTokenAsQueryParam;
		String message;
		if ((environment.getProperty("application.authorization.token.type") + " " + environment.getProperty("application.authorization.token")).equals(authorizationToken)) {
			try {
				nexusConnectorGroup.clearCache();
			} catch (NullPointerException exc) {
				if (nexusConnectorGroup == null) {
					logger.warn("The Nexus connector group is disabled");
				} else {
					throw exc;
				}
			}
			try {
				gitHubConnector.clearCache();
			} catch (NullPointerException exc) {
				if (gitHubConnector == null) {
					logger.warn("The GitHub connector is disabled");
				} else {
					throw exc;
				}
			}
			cache.clear();
			message = "Cache successfully cleaned";
		} else {
			logger.error(message = "Cannot clear cache: unauthorized");
		}
		response.sendRedirect("stats/artifact-download-chart.html?message=" + message);
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

	private Integer getStarCountOrNull(String username, String repositoryName) {
		try {
			try {
				return gitHubConnector.getAllStarCount(username, repositoryName);
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


	public class InitializeException extends Exception {

		private static final long serialVersionUID = -8992396547359618654L;

		public InitializeException(String message, Throwable cause) {
			super(message, cause);
		}

		public InitializeException(String message) {
			super(message);
		}
	}
}
