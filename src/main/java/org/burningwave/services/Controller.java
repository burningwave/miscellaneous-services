package org.burningwave.services;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;

import org.burningwave.Badge;
import org.burningwave.SimpleCache;
import org.springframework.core.env.Environment;
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

	public Controller(
		Environment environment,
		SimpleCache cache,
		Badge badge,
		NexusConnector.Group nexusConnectorGroup,
		GitHubConnector gitHubConnector
	) {
		this.environment = environment;
		this.cache = cache;
		this.badge = badge;
		this.nexusConnectorGroup = nexusConnectorGroup;
		this.gitHubConnector = gitHubConnector;
	}

	@GetMapping(path = "/nexus-connector/project-info", produces = "application/json")
	public Object getProjectInfo() {
		return nexusConnectorGroup.getAllProjectInfos();
	}

	@GetMapping(path = "/stats/total-downloads", produces = "application/json")
	public Object getTotalDownloads(
		@RequestParam(value = "groupId", required = false) Set<String> groupIds,
		@RequestParam(value = "artifactId", required = false) String artifactId,
		@RequestParam(value = "startDate", required = false) String startDate,
		@RequestParam(value = "months", required = false) String months
	) {
		Long value = getTotalDownloadsOrNull(groupIds, artifactId, startDate, months);
		return value != null? value : "null";
	}

	@GetMapping(path = "/stats/downloads-for-month", produces = "application/json")
	public Object getDownloadsForMonth(
		@RequestParam(value = "groupId", required = false) Set<String> groupIds,
		@RequestParam(value = "artifactId", required = false) String artifactId,
		@RequestParam(value = "startDate", required = false) String startDate,
		@RequestParam(value = "months", required = false) String months
	) {
		try {
			return nexusConnectorGroup.getAllStats(
				groupIds,
				artifactId,
				startDate != null ? new SimpleDateFormat("yyyy-MM").parse(startDate) : null,
				months != null ? Integer.valueOf(months) : null
			).getDownloadsForMonth();
		} catch (Throwable exc) {
			logger.error("Exception occurred", exc);
			return "null";
		}
	}

	@GetMapping(path = "/stats/total-downloads-badge", produces = "image/svg+xml")
	public Object getTotalDownloadsBadge(
		@RequestParam(value = "groupId", required = false) Set<String> gropuIds,
		@RequestParam(value = "artifactId", required = false) String artifactId,
		@RequestParam(value = "startDate", required = false) String startDate,
		@RequestParam(value = "months", required = false) String months,
		HttpServletResponse response
	) throws JAXBException, ParseException, InterruptedException, ExecutionException {
		response.setHeader("Cache-Control", "no-store");
		String label = "artifact downloads";
		return badge.build(
			getTotalDownloadsOrNull(gropuIds, artifactId, startDate, months),
			artifactId != null ? artifactId + " " + label : label,
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
			repositoryName != null ? repositoryName + " " + label : label,
			"GitHub stars", "#78e", 93
		);
	}

	@GetMapping(path = "/clear-cache")
	public void clearCache(
		@RequestHeader(value = "Authorization", required = false) String authorizationToken,
		HttpServletResponse response
	) throws IOException {
		if ((environment.getProperty("application.authorization.token.type") + " " + environment.getProperty("application.authorization.token")).equals(authorizationToken)) {
			nexusConnectorGroup.clearCache();
			gitHubConnector.clearCache();
			cache.clear();
		} else {
			logger.error("Cannot clear cache: unauthorized");
		}
		response.sendRedirect("https://www.burningwave.org/");
	}

	private Long getTotalDownloadsOrNull(Set<String> groupIds, String artifactId, String startDate, String months) {
		try {
			return nexusConnectorGroup.getAllStats(
				groupIds,
				artifactId,
				startDate != null ? new SimpleDateFormat("yyyy-MM").parse(startDate) : null,
				months != null ? Integer.valueOf(months) : null
			).getTotalDownloads();
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
			return gitHubConnector.getAllStarCount(username, repositoryName);
		} catch (Throwable exc) {
			logger.error("Exception occurred", exc);
			return null;
		}
	}

}
