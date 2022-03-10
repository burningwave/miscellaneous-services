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

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@org.springframework.web.bind.annotation.RestController
@RequestMapping("/miscellaneous-services/")
@CrossOrigin
@OpenAPIDefinition(
    info = @Info(
        title="Miscellaneous services",
        version = "10.0.0"
    )
)
public class RestController {
	private final static org.slf4j.Logger logger;

	private NexusConnector.Group nexusConnectorGroup;

    static {
    	logger = org.slf4j.LoggerFactory.getLogger(RestController.class);
    }

	public RestController (
		@Nullable HerokuConnector herokuConnector,
		@Nullable NexusConnector.Group nexusConnectorGroup
	) throws InitializeException {
		this.nexusConnectorGroup = nexusConnectorGroup;
	}

	@GetMapping(path = "/nexus-connector/project-info", produces = "application/json")
	public Collection<String[]> getProjectInfo() {
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
			return null;
		}

	}

	@GetMapping(path = "/stats/total-downloads", produces = "application/json")
	public Long getTotalDownloads(
		@RequestParam(value = "groupId", required = false) Set<String> groupIds,
		@RequestParam(value = "alias", required = false) Set<String> aliases,
		@RequestParam(value = "artifactId", required = false) Set<String> artifactIds,
		@RequestParam(value = "startDate", required = false) String startDate,
		@RequestParam(value = "months", required = false) String months
	) {
		return getTotalDownloadsOrNull(groupIds, aliases, artifactIds, startDate, months);
	}

	@GetMapping(path = "/stats/downloads-for-month", produces = "application/json")
	public List<Integer> getDownloadsForMonth(
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
					return null;
				}
				throw exc;
			}
		} catch (Throwable exc) {
			logger.error("Exception occurred", exc);
			return null;
		}
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

}
