package org.burningwave;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class Badge {

	private String badgeTemplate;


	public Badge() {
		badgeTemplate = new BufferedReader(
        	new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream("templates/badge.xml"), StandardCharsets.UTF_8)
        ).lines().collect(Collectors.joining("\n"));
	}

	public String build(Number effectiveValue, String label, String rightBlockColor, int width) {
		Long value = effectiveValue != null ?
			effectiveValue.longValue()
			: 1000L;
		long bound = 10;
	    int rightBlockWidth = 11;
	    while (bound <= value) {
	        width += 8;
	        rightBlockWidth += 8;
	        bound *= 10;
	    }
	    int labelPosition = (width - rightBlockWidth) * 5;
	    int valuePosition = (labelPosition * 2) + (rightBlockWidth * 5);
	    return badgeTemplate
	    	.replace("${width}", Integer.toString(width))
	    	.replace("${rightBlockWidth}", Integer.toString(rightBlockWidth))
	    	.replace("${rightBlockPosition}", Integer.toString(width - rightBlockWidth))
	    	.replace("${rightBlockColor}", rightBlockColor)
	    	.replace("${labelPosition}", Integer.toString(labelPosition))
	    	.replace("${labelShadowPosition}", Integer.toString(labelPosition + 10))
	    	.replace("${valuePosition}", Integer.toString(valuePosition))
	    	.replace("${valueShadowPosition}", Integer.toString(valuePosition + 10))
	    	.replace("${title}", label)
		    .replace("${label}", label)
		    .replace("${value}", effectiveValue != null ? Long.toString(value) : "NaN");
	}
}
