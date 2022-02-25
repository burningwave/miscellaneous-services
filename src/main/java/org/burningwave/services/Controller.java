package org.burningwave.services;

import javax.servlet.http.HttpServletRequest;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;


@org.springframework.stereotype.Controller
@RequestMapping("/miscellaneous-services/")
@CrossOrigin
public class Controller {

    @GetMapping("/stats/artifact-download-chart")
    public String loadArtifactDownloadChart(HttpServletRequest request, Model model) {
    	String url = request.getRequestURL().toString();
    	String basePath = url.substring(0, url.indexOf("/miscellaneous-services"));
    	model.addAttribute("basePath", basePath);
        return "artifact-download-chart";
    }

}
