<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<c:if test="${empty message}">
    <c:set var = "message" scope = "page" value = "null"/>
</c:if>
<html>
	<head>
		<link rel="shortcut icon" type="image/png" href="/image/favicon.png">
		<meta content="width=device-width, initial-scale=1" name="viewport" />
		<meta property="og:title" content="Artifact downloads from Maven Central" />
		<meta property="og:description" content="A dashboard with download statistics" />
		<meta property="og:image" content="<c:out value="${basePath}"/>/image/logo.png">
		<meta property="og:image:width" content="572" />
		<meta property="og:image:height" content="546" />		
		<meta property="twitter:title" content="Artifact downloads from Maven Central" />		
		<meta name="twitter:card" content="summary" />
		<meta name="theme-color" content="#e54d1d">
		<style>
			
			div.header {
			    position: fixed;
				top: 0;
				max-width: 100%;
				width: 100%;
			    height: 180px;
				border-radius: 0px 0px 10px 10px;
				background:  #e54d1d;
				color: #ffffff;
			}
			
			div.container { 
			    margin-top: 200px;
				margin-right: 20px;
				margin-left: 20px;
				margin-bottom: 40px;
			}
			
			div.footer {
				position: fixed;
				bottom: 0;
				right: 0;
				margin-left: 3px;
				margin-right: 3px;
				background:  #ffffff;
				text-align:right;
				color: #000000;
			}
			
			@media screen and (min-width: 63px) {
			    div.header {
			    	height: 160px;
			    }
				div.container {
			    	margin-top: 140px;
			    }
			}
			
			@media screen and (min-width: 77px) {
			    div.header {
			    	height: 100px;
			    }
				div.container {
			    	margin-top: 120px;
			    }
			}
			
			@media screen and (min-width: 593px) {
			    div.header {
			    	height: 60px;
			    }
				div.container {
			    	margin-top: 80px;
			    }
			}
			
			
			#loader { 
		        border: 12px solid #f7bc12; 
		        border-radius: 50%; 
		        border-top: 12px solid #e54d1d; 
		        width: 35px; 
		        height: 35px; 
		        animation: spin 1s linear infinite; 
		    } 
			
			@font-face {
			    font-family: 'Aldo Pro Book';
			    src: local('Aldo Pro Book'), local('Aldo-Pro-Book'),
			        url('/font/AldoPro-Bold.woff2') format('woff2'),
			        url('/font/AldoPro-Bold.woff') format('woff'),
			        url('/font/AldoPro-Bold.ttf') format('truetype');
			    font-weight: normal;
			    font-style: normal;
			}
		        
		    @keyframes spin { 
		        100% { 
		            transform: rotate(360deg); 
		        } 
		    } 
		        
		    .center { 
		        position: absolute; 
		        top: 0; 
		        bottom: 0; 
		        left: 0; 
		        right: 0;
		        margin: auto; 
		    }
		    #overlay {
		        display:table;
		        position: fixed;
		        width: 100%;
		        height: 100%;
		        top: 0;
		        left: 0;
		        right: 0;
		        bottom: 0;
		        background-color: rgba(0,0,0,0.8);
		        z-index: 2;
		        cursor: pointer;
		    }
		
		    #overlay span{
		        color: #f7bc12;
		        font-size: 18px;
		        display:table-cell;
		        vertical-align:middle;
		        text-align:center;
		    }

			a:link {
				color: #e54d1d;
				text-decoration: none;
			}
			
			a:visited {
				color: #e54d1d;
			}
			
			a:link:hover {
				text-decoration: underline;
			}
			
			
		</style>
	</head>
	<body style="margin: 0px; font-family: 'Helvetica Neue', 'Helvetica', 'Arial', 'sans-serif';">
		<div class="header">
			<h1 style="margin: 12px; font-weight: 500;">Artifact downloads from Maven Central</h1>
		</div>
		<div class="container">
			<div id="overlay">
				<div id="loader" class="center"></div>
				<span><br/><br/><br/><br/><br/><b>LOADING</b></span>
			</div>
			<div>
				<p style="color: #666666;"><strong>Download counts are automatically updated after the <c:out value="${daysOfTheMonthFromWhichToLeave}"/> day of each month by connecting directly to Maven Central Statistics</strong>.</p>
			</div>
			<div style="display: table;" id="downloadsSummary">

			</div>
			<div id="chartContainer">
				<div id="monthlyTrendChartDiv" style="max-width: 100%; margin: auto;">
					<canvas id="monthlyTrendChart"></canvas>
				</div>
				<div id="separatorDivOne" style="height: 50px;">
				</div>
				<div id="overallTrendChartDiv" style="max-width: 100%; margin: auto;">
					<canvas id="overallTrendChart"></canvas>
				</div>
			</div>
		</div>
		<div class="footer">
			Powered by
			<a href="#" onclick="goToUrl('https://burningwave.github.io/miscellaneous-services/', 7500);return false;">
				<span style="font-family: 'Aldo Pro Book'; font-size: 23px; font-style: italic; font-weight: bold; color: #e54d1d;">Burningwave</span>
			</a>
		</div>
	</body>

</html>

<script src="/js/jquery-3.6.0.min.js"></script>
<script src="/js/Chart.min.js"></script>
<script src="/js/moment.min.js"></script>
<script>
	
	var defaultDateAsString = '<c:out value="${startDate}"/>-01';
	var allProjectInfos;
	var totalRowTextColor = 'rgb(0, 0, 0)';
	var startDate;
	var monthlyTrendChartDatasets = [];
    var overallTrendChartDatasets = [];
	var loadedArtifactIds = [];
    var attemptedLoadingArtifactIds = [];
    var months;
    var artifactIds;
    var overallTrendChart;
    var monthlyTrendChart;
    var messages = <c:out value="${message}" escapeXml="false" />;
	var groupIdsQueryParam = toArray(getQueryParam("groupId"));
	var artifactIdsQueryParam = toArray(getQueryParam("artifactId"));
	var aliasQueryParam = toArray(getQueryParam("alias"));
	var startDateQueryParam = getQueryParam("startDate");
	var monthsQueryParam = getQueryParam("months");
	
	//var pathname = window.location.pathname;
	if (messagesContains('<%= org.burningwave.services.Controller.SWITCH_TO_REMOTE_APP_SUCCESSFUL_MESSAGE %>')) {
		showMessages();
		sleep(60000);
		goToUrl('/miscellaneous-services/stats/artifact-download-chart', 7500);
	} else {
		loadPageContent();
		showMessages();
	}
    
	
    function loadPageContent() {
    	allProjectInfos = getAllProjectInfos();
		artifactIds = selectProjectInfos(groupIdsQueryParam, artifactIdsQueryParam, aliasQueryParam);
		buildSummary(artifactIds);

        startDate = startDateQueryParam != null ? moment(startDateQueryParam + '-01') : moment(defaultDateAsString);
		var startDateForComputation = startDateQueryParam != null ? moment(startDateQueryParam + '-01') : moment(defaultDateAsString);
        var endDate = (monthsQueryParam != null ? startDateForComputation.add(monthsQueryParam,'month') : moment()).startOf('month').add(-1,'day');
        var timeValues = [];
		startDateForComputation = startDateQueryParam != null ? moment(startDateQueryParam + '-01') : moment(defaultDateAsString);		
		
        while (endDate > startDateForComputation || startDateForComputation.format('M') === endDate.format('M')) {
            timeValues.push(startDateForComputation.format('MMM YYYY'));
            startDateForComputation.add(1,'month');
			months = months != null ? months + 1 : 1;
        }

        var showOverallTrendChart = getQueryParam("show-overall-trend-chart");
        if (showOverallTrendChart == null || showOverallTrendChart.toUpperCase() != "false".toUpperCase()) {
            overallTrendChart = createChart('overallTrendChart', 'Overall trend', timeValues, overallTrendChartDatasets);
        } else {
            document.getElementById("overallTrendChartDiv").style.display = "none";
        }
        var showMonthlyTrendChart = getQueryParam("show-monthly-trend-chart");
        if (showMonthlyTrendChart == null || showMonthlyTrendChart.toUpperCase() != "false".toUpperCase()) {
            monthlyTrendChart = createChart('monthlyTrendChart', 'Monthly trend', timeValues, monthlyTrendChartDatasets);
        } else {
            document.getElementById("monthlyTrendChartDiv").style.display = "none";
        }
        if (overallTrendChart == null || monthlyTrendChart == null) {
            document.getElementById("separatorDivOne").style.display = "none";
        }

        for (i = 0; i < artifactIds.length; i++) {
            launchAsyncCall(artifactIds[i], startDate.format('YYYY-MM'), months);
        }
        if (artifactIds.length == 0) {
        	displayError();
        }
    }
    
    
	function selectProjectInfos(groupIdValues, artifactIdValues, aliasValues) {
		var artifactIds = [];
		for (i = 0; i < allProjectInfos.length; i++) {
			if (
				(groupIdValues == null || groupIdValues.includes(allProjectInfos[i][0].split(":")[0])) &&
				((artifactIdValues == null && aliasValues == null) || 
				(artifactIdValues != null && containsArtifactId(groupIdValues, artifactIdValues, allProjectInfos[i][0])) || 
				(aliasValues != null && aliasValues.includes(allProjectInfos[i][1])))
			) {
				artifactIds.push(allProjectInfos[i][0]);
			}		
		}
		return artifactIds;	
	}
	
	
	function sleep(milliseconds) {
		var start = new Date().getTime();
		var end=0;
		while( (end-start) < milliseconds){
			end = new Date().getTime();
		}
	}
	
	
	function containsArtifactId(groupIdValues, artifactIdValues, artifactId) {
		for (j = 0; j < artifactIdValues.length; j++) {
			var artifactIdSplitted = artifactIdValues[j].split(":");
			if (artifactIdSplitted.length > 1 && artifactId == artifactIdValues[j]) {
				return true;
			} else if (artifactIdSplitted.length == 1) {
				if (groupIdValues != null) {
					for (k = 0; k < groupIdValues.length; k++) {
						if ((groupIdValues[k] + ":" + artifactIdSplitted[0]) == artifactId) {
							return true;
						}
					}	
				} else if (artifactIdSplitted[0] == artifactId.split(":")[1]) {
					return true;
				}
			}
		}
		return false;
	}
	
	
	function getAllProjectInfos() {
		var values = getAllProjectInfosFromRemote();
		for (i = 0; i < values.length; i++) {
            values[i][2] = hexToRgb(values[i][2]);
        }
		return values;
	}
	
	
	function hexToRgb(hex) {
		hex = '#' + hex;
		// Expand shorthand form (e.g. "03F") to full form (e.g. "0033FF")
		var shorthandRegex = /^#?([a-f\d])([a-f\d])([a-f\d])$/i;
		hex = hex.replace(shorthandRegex, function(m, r, g, b) {
			return r + r + g + g + b + b;
		});	
		var result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
		var rgbString = 'rgb(' + parseInt(result[1], 16) + ', ' + parseInt(result[2], 16) + ', ' + parseInt(result[3], 16) + ')';
		return rgbString;
	}
	
	
	function toArray(variable) {
		if (variable == null) {
			return null;
		} else if (Array.isArray(variable)) {
			return variable;
		} else {
			return [variable];
		}
	}
	
	
	function getQueryParam(key, target){
		var values = [];
		if (!target) target = location.href;

		key = key.replace(/[\[]/, "\\\[").replace(/[\]]/, "\\\]");

		var pattern = key + '=([^&#]+)';
		var o_reg = new RegExp(pattern,'ig');
		while (true){
			var matches = o_reg.exec(target);
			if (matches && matches[1]){
				values.push(matches[1]);
			} else {
				break;
			}
		}

		if (!values.length){
			return null;   
		} else {
			return values.length == 1 ? values[0] : values;
		}
	}
	
	
	function buildSummary(artifactIds) {
		for (j = 0; j < artifactIds.length; j++) {
			var summaryItem = buildSummaryItem(artifactIds[j], getColorOrRandomColor(artifactIds[j]), getSite(artifactIds[j]));
			jQuery("#downloadsSummary").append(summaryItem);
		}
		if (artifactIds.length > 1) {
			jQuery("#downloadsSummary").append(buildSummaryItem(null, 'rgb(0, 0, 0)', null));
			jQuery("#downloadsSummary").append(buildSummaryItem('Total', totalRowTextColor, null));
		}
	}
	
	
	function buildSummaryItem(artifactId, color, site) {
		var label = site != null ?
			'<a href="' + site +'" style="color:' + color + ';">' + (artifactId != null ? artifactId : '') + '</a>' :
			(artifactId != null ? artifactId : '');
		var item = 
			'<div id="' + artifactId + 'DownloadsRow" style="display: table-row; font-size: 14px;">' +
			'	<div style="display: table-cell; color: ' + color + ';">' +
			'   	<b>' + label + '</b>' + (artifactId != null ? ':' : '') + '&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;' +
			'	</div>' +
			'	<div id="' + artifactId + 'Downloads" style="display: table-cell; text-align: right; color: grey;">' +
			'	</div>' +
			'</div>';
		return item;
	}
	
	
	function overlayOff() {
		document.getElementById("overlay").style.display = "none";
		document.documentElement.style.overflow = 'scroll';
		document.body.scroll = "yes";	
	}
	
	
	function overlayOn() {
		document.getElementById("overlay").style.display = "";
		document.documentElement.style.overflow = 'hidden';
		document.body.scroll = "no";
	}
	

    function buildTotalTrendAndShowChart() {
        if (loadedArtifactIds.length == artifactIds.length && monthlyTrendChartDatasets[0] != null) {
            var totalDownloads = [];
            var longestDataset = monthlyTrendChartDatasets[0].data;
            if (monthlyTrendChartDatasets.length > 1) {
            	for (j = 1; j < monthlyTrendChartDatasets.length; j++) {
                	if (longestDataset.length < monthlyTrendChartDatasets[j].data.length) {
                		longestDataset = monthlyTrendChartDatasets[j].data;
                	}
                }
            }
            for (j = 0; j < longestDataset.length; j++) {
            	totalDownloads.push(0);            	
            }
            for (i = 0; i < monthlyTrendChartDatasets.length; i++) {
            	for (j = 0; j < monthlyTrendChartDatasets[i].data.length; j++) {
            		totalDownloads[j] += monthlyTrendChartDatasets[i].data[j];  
            	}
            }
            for (j = 0; j < totalDownloads.length; j++) {
            	if (totalDownloads[j] == 0) {
            		totalDownloads[j] = null;
            	} else {
            		break;
            	}	
            }
            if (artifactIds.length > 1) {
	            var overallDownloads = buildChartDataSets(monthlyTrendChartDatasets, overallTrendChartDatasets, 'Total', totalDownloads,
	                totalRowTextColor, totalRowTextColor
	            );
	            document.getElementById("TotalDownloads").appendChild(document.createTextNode(formatNumber(overallDownloads[overallDownloads.length - 1])));
            }
		}
        if (attemptedLoadingArtifactIds.length == artifactIds.length) {
            overlayOff(); 
        }
        updateCharts();
    }
	
	
	function formatNumber(number, minimumFractionDigits) {
		if (minimumFractionDigits == null) {
			minimumFractionDigits = 0;
		}
		return (number).toLocaleString(
		  undefined,
		  { minimumFractionDigits: 0 }
		);
	}
	
	
    function getLabel(name) {
        return name;
    }


    function updateCharts() {
        if (overallTrendChart != null) {
            overallTrendChart.update();
        }
        if (monthlyTrendChart != null) {
            monthlyTrendChart.update();
        }
    }


    function getBackgroundColor(artifactId) {
		return getColorOrRandomColor(artifactId);
    }


    function getBorderColor(artifactId){
		return getColorOrRandomColor(artifactId);
    }
	
    
	function getColorOrRandomColor(artifactId) {
		var artifactInfos;
		for (i = 0; i < allProjectInfos.length; i++) {
			if (allProjectInfos[i][0] == artifactId) {
				artifactInfos = allProjectInfos[i];
				break;				
			}
        }
		if (artifactInfos == null) {
			artifactInfos = [artifactId, null];
			allProjectInfos.push(artifactInfos);
		}
		if (artifactInfos[2] == null) {
			artifactInfos[2] = 'rgb(' + Math.floor(Math.random() * 256) + ', ' + Math.floor(Math.random() * 256) + ', ' + Math.floor(Math.random() * 256) + ')';
		}
		return artifactInfos[2];
	}
	
	
	function getSite(artifactId) {
		var artifactInfos;
		for (i = 0; i < allProjectInfos.length; i++) {
			if (allProjectInfos[i][0] == artifactId) {
				artifactInfos = allProjectInfos[i];
				break;				
			}
        }
		return artifactInfos != null ? artifactInfos[3] : null;
	}


    function launchAsyncCall(artifactIdInputParam, startDateInputParam, monthsInputParam) {
        var startDateParam = startDateInputParam != null ? '&startDate=' + startDateInputParam : '';
        var monthsParam = monthsInputParam != null ? '&months=' + monthsInputParam : '';
        var url = '/miscellaneous-services/stats/downloads-for-month?' +
			'groupId=' + artifactIdInputParam.split(':')[0] + '&' +
			'artifactId=' + artifactIdInputParam.split(':')[1] + '&' +
			startDateParam + monthsParam;
        return jQuery.ajax({
            url: url,
            data: null,
            error: function (jqXhr, textStatus, errorMessage) { // error callback 
    			attemptedLoadingArtifactIds.push(artifactIdInputParam);
    			displayError(artifactIdInputParam);
            },
            dataType: 'json',
            success: function(artifactDownloads) {
				if (artifactDownloads != null) {
					loadedArtifactIds.push(artifactIdInputParam);
					attemptedLoadingArtifactIds.push(artifactIdInputParam);
					buildChartData(monthlyTrendChartDatasets, overallTrendChartDatasets,
						artifactIdInputParam,
						getLabel(artifactIdInputParam), artifactDownloads, 
						getBackgroundColor(artifactIdInputParam),
						getBorderColor(artifactIdInputParam)
					);
					buildTotalTrendAndShowChart();
				} else {
	    			attemptedLoadingArtifactIds.push(artifactIdInputParam);
	    			displayError(artifactIdInputParam);
				}
            },
            type: 'GET'
        });
    }

    
	function getAllProjectInfosFromRemote() {
        var url = '/miscellaneous-services/nexus-connector/project-info';
        var response = jQuery.ajax({
            url: url,
            data: null,
            error: function (jqXhr, textStatus, errorMessage) {
            	if (messages == null) {
            		messages = [];
            	}
            	messages.push('Cannot load project informations');
            },
            dataType: 'json',
            type: 'GET',
			async: false
        });
        var responseJSon;
        if (response != null) {
        	responseJSon = response.responseJSON
        }
        if (responseJSon == null) {
        	responseJSon = [];
        }
        return responseJSon;
	}


    function buildChartData(monthlyTrendChartDatasets, overallTrendChartDatasets, artifactId, label, downloadsData, backgroundColor,  borderColor) {
        if (downloadsData != null && downloadsData.length > 0) {
            if (downloadsData[downloadsData.length - 1] != null) {
                var idx = downloadsData.length - 1;
                var popCount = 0;
                while (downloadsData[idx] == 0) {
                    popCount++;
                    idx--;
                }
                for (let i = 0; i < popCount; i++) {
                    downloadsData.pop();
                } 
                var overallDownloads = buildChartDataSets(
                    monthlyTrendChartDatasets, overallTrendChartDatasets, label,
                    downloadsData, backgroundColor, borderColor
                );
                document.getElementById(artifactId + "Downloads").appendChild(
                    document.createTextNode(formatNumber(overallDownloads[overallDownloads.length - 1]))
                );
            } else {
                document.getElementById(artifactId + "DownloadsRow").style.display = "none";             
            }
        }
    }


    function buildChartDataSets(monthlyTrendChartDatasets, overallTrendChartDatasets, label, downloadsData, backgroundColor,  borderColor) {
        monthlyTrendChartDatasets.push(createDataSet(label, downloadsData, backgroundColor, borderColor));
        var overallDownloads = [downloadsData[0]];
        for (i = 1; i < downloadsData.length; i++) {
            if (overallDownloads[i - 1] != null) {
                overallDownloads[i] = overallDownloads[i - 1] + downloadsData[i];
            } else {
                overallDownloads[i] = downloadsData[i];
            }
        }
        overallTrendChartDatasets.push(createDataSet(label, overallDownloads, backgroundColor, borderColor));
        return overallDownloads;
    }


    function createChart(id, name, labels, dataSets) {
        var ctx = document.getElementById(id);
        ctx.height = 400;
        return new Chart(ctx.getContext('2d'), {
            type: 'line',
            data: {
                labels: labels,
                datasets: dataSets
            },
            options: createOptions(name)
        });
    }


    function createDataSet(label, data, backgroundColor, borderColor) {
        return {
            label: label,
            fill: false,
            data: data,
            borderWidth: 3,
            pointRadius: 2,
            pointHoverRadius: 4,
            lineTension: 0.4,
            backgroundColor: backgroundColor,
            borderColor: borderColor		
        };
    }


    function createOptions(title) {
        return {
            scales: {
                yAxes: [{
                    ticks: {
                        beginAtZero: true
                    },
                    gridLines: {
                        drawOnChartArea: true
                    }
                }],
                xAxes: [{
                    gridLines: {
                        drawOnChartArea: false
                    }
                }]
            },
            title: {
                display: true,
                fontSize: 28,
                text: title
            },
            tooltips: {
                mode: 'index',
                intersect: false,
            },
            animation: {
                duration: 0
            },
            legend: {
        		display: false
        	},
            maintainAspectRatio: false
        };
    }
	
    
	function messagesContains(message) {
		return messages != null && messages.includes(message);
	}
	
	
	function showMessages() {
		if (messages != null) {
			for (i = 0; i < messages.length; i++) {
	            alert(decodeURIComponent(messages[i]));
	        }
			messages = null;
		}
	}
    
	
    function displayError(id) {
        var errorMessage = "Could not retrieve download count from Maven Central: try again later or tomorrow";
        var node;
        if (id != null) {
	        node = document.getElementById(id + "Downloads");
	        while (node.firstChild) {
	            node.removeChild(node.firstChild);
	        }
	        node.appendChild(document.createTextNode(errorMessage));
        }
        node = document.getElementById("TotalDownloads");
        if (node != null) {
	        while (node.firstChild) {
	            node.removeChild(node.firstChild);
	        }
	        node.appendChild(document.createTextNode(errorMessage));
        }
        if (attemptedLoadingArtifactIds.length == artifactIds.length) {
            overlayOff(); 
        }
    }
	
	
	function goToUrl(url, timeout) {
		overlayOn();
		window.location.href = url;
		setTimeout(overlayOff, timeout);
		return false;
	}
	
</script>