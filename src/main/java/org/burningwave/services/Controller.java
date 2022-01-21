package org.burningwave.services;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;

import org.burningwave.Badge;
import org.burningwave.SimpleCache;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/miscellaneous-services/")
@CrossOrigin
public class Controller {
	private final static org.slf4j.Logger logger;
	private NexusConnector nexusConnector;
	private GitHubConnector gitHubConnector;
	private SimpleCache cache;
	private Badge badge;

    static {
    	logger = org.slf4j.LoggerFactory.getLogger(Controller.class);
    }

	public Controller(SimpleCache cache, Badge badge, NexusConnector nexusConnector, GitHubConnector gitHubConnector) {
		this.cache = cache;
		this.badge = badge;
		this.nexusConnector = nexusConnector;
		this.gitHubConnector = gitHubConnector;
	}


	@GetMapping(path = "/stats/total-downloads", produces = "application/json")
	public Object getTotalDownloads(
		@RequestParam(value = "artifactId", required = false) String artifactId,
		@RequestParam(value = "startDate", required = false) String startDate,
		@RequestParam(value = "months", required = false) String months
	) {
		Long value = getTotalDownloadsOrNull(artifactId, startDate, months);
		return value != null? value : "null";
	}

	@GetMapping(path = "/stats/downloads-for-month", produces = "application/json")
	public Object getDownloadsForMonth(
		@RequestParam(value = "artifactId", required = false) String artifactId,
		@RequestParam(value = "startDate", required = false) String startDate,
		@RequestParam(value = "months", required = false) String months
	) {
		try {
			return nexusConnector.getAllStats(
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
		@RequestParam(value = "artifactId", required = false) String artifactId,
		@RequestParam(value = "startDate", required = false) String startDate,
		@RequestParam(value = "months", required = false) String months,
		HttpServletResponse response
	) throws JAXBException, ParseException, InterruptedException, ExecutionException {
		response.setHeader("Cache-Control", "no-store");
		String label = "artifact downloads";
		return badge.build(
			getTotalDownloadsOrNull(artifactId, startDate, months),
			artifactId != null ? label + " " + artifactId : label,
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
			repositoryName != null ? label + " " + repositoryName : label,
			"GitHub stars", "#78e", 93
		);
	}

	@GetMapping(path = "/clear-cache")
	public void clearCache(HttpServletResponse response) throws IOException {
		nexusConnector.clearCache();
		gitHubConnector.clearCache();
		cache.clear();
		response.sendRedirect("https://www.burningwave.org/");
	}

	private Long getTotalDownloadsOrNull(String artifactId, String startDate, String months) {
		try {
			return nexusConnector.getAllStats(
				artifactId,
				startDate != null ? new SimpleDateFormat("yyyy-MM").parse(startDate) : null,
				months != null ? Integer.valueOf(months) : null
			).getTotalDownloads();
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
