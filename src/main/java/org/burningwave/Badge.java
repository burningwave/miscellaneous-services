       package org.burningwave;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class Badge {

	private String badgeTemplate;
	private Utility utility;

	public Badge(Utility utility) {
		badgeTemplate = new BufferedReader(
        	new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream("templates/badge.xml"), StandardCharsets.UTF_8)
        ).lines().collect(Collectors.joining("\n"));
		this.utility = utility;
	}

	public String build(
		Number effectiveValue,
		String title,
		String label,
		String rightBlockColor,
		int width
	) {
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
	    	.replace(utility.toPlaceHolder("width"), Integer.toString(width))
	    	.replace(utility.toPlaceHolder("rightBlockWidth"), Integer.toString(rightBlockWidth))
	    	.replace(utility.toPlaceHolder("rightBlockPosition"), Integer.toString(width - rightBlockWidth))
	    	.replace(utility.toPlaceHolder("rightBlockColor"), rightBlockColor)
	    	.replace(utility.toPlaceHolder("labelPosition"), Integer.toString(labelPosition))
	    	.replace(utility.toPlaceHolder("labelShadowPosition"), Integer.toString(labelPosition + 10))
	    	.replace(utility.toPlaceHolder("valuePosition"), Integer.toString(valuePosition))
	    	.replace(utility.toPlaceHolder("valueShadowPosition"), Integer.toString(valuePosition + 10))
	    	.replace(utility.toPlaceHolder("title"), title)
		    .replace(utility.toPlaceHolder("label"), label)
		    .replace(utility.toPlaceHolder("value"), effectiveValue != null ? Long.toString(value) : "NaN");
	}
}
