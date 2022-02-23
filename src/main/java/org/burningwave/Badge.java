/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: https://github.com/burningwave/miscellaneous-services
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019-2022 Roberto Gentili
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
