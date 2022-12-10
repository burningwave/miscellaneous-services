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
package org.burningwave;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Utility {
	private final static Random randomizer;

	static {
		randomizer = new Random();
	}

	public byte[] serialize(Serializable object) throws IOException {
		try (ByteArrayOutputStream bAOS = new ByteArrayOutputStream(); ObjectOutputStream oOS = new ObjectOutputStream(bAOS);) {
	        oOS.writeObject(object);
	        return bAOS.toByteArray();
		}
	}

	public String toBase64(Serializable object) throws IOException {
		return Base64.getEncoder().encodeToString(serialize(object));
	}

	@SuppressWarnings("unchecked")
	public <T extends Serializable> T deserialize(byte[] objectAsBytes) throws IOException, ClassNotFoundException {
		ByteArrayInputStream bAIS = new ByteArrayInputStream(objectAsBytes);
        ObjectInputStream oIS = new ObjectInputStream(bAIS);
        Object o  = oIS.readObject();
        oIS.close();
        bAIS.close();
        return (T) o;
	}

	public <T extends Serializable> T fromBase64(String objectAsString) throws IOException, ClassNotFoundException {
		return deserialize(Base64.getDecoder().decode(objectAsString));
	}

	public boolean delete(File file) {
		return delete(file, true);
	}

	public boolean delete(File file, boolean deleteItSelf) {
		if (file.isDirectory()) {
		    File[] files = file.listFiles();
		    if(files != null) { //some JVMs return null for empty dirs
		        for(File fsItem: files) {
		            delete(fsItem, true);
		        }
		    }
		}
		if (deleteItSelf && !file.delete()) {
    		file.deleteOnExit();
    		return false;
    	}
		return true;
	}


	public Calendar newCalendarAtTheStartOfTheMonth() {
		Calendar calendar = new GregorianCalendar();
		calendar.setTime(new Date());
		return setCalendarAtTheStartOfTheMonth(calendar);
	}

	public Calendar setCalendarAtTheStartOfTheMonth(Calendar calendar) {
		calendar.set(Calendar.DATE, 0);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar;
	}

	public String toPlaceHolder(String variable) {
		return "${" + variable + "}";
	}

	public String randomHex() {
		return String.format("%06x", randomizer.nextInt(0xffffff + 1));
	}

	public <T> void setIfNotNull(Consumer<T> target, Supplier<T> source) {
		T value = source.get();
		if (value != null) {
			target.accept(value);
		}
	}

}