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

public class Utility {

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
}
