package org.burningwave;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

public class FSBasedCache extends SimpleCache.Abst {
	private final static org.slf4j.Logger logger;

	@Autowired
	private Utility utility;

	private String basePath;

    static {
    	logger = org.slf4j.LoggerFactory.getLogger(FSBasedCache.class);
    }

	public FSBasedCache(Map<String, Object> configMap) {
		basePath = ((String)configMap.get("base-path")).replace("\\", "/");
		File file = new File(basePath);
		file.mkdirs();
		logger.info("File system based cache successfully instantiated");
	}


	@Override
	public void store(String key, Serializable object) {
		try (
			FileOutputStream fout = new FileOutputStream(basePath + "/" + Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8)) + ".ser");
			ObjectOutputStream oos = new ObjectOutputStream(fout)
		) {
			oos.writeObject(object);
			logger.info("Object with id '{}' stored in the physical cache", key);
		} catch (Throwable exc) {
			Throwables.rethrow(exc);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends Serializable> T  load(String key) {
		try (FileInputStream fIS = new FileInputStream(basePath + "/" + Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8)) + ".ser");
			ObjectInputStream oIS = new ObjectInputStream(fIS)) {
			T effectiveItem = (T) oIS.readObject();
			logger.info("Object with id '{}' loaded from physical cache: {}", key, effectiveItem);
	        return effectiveItem;
		} catch (FileNotFoundException exc) {
			return null;
		} catch (Throwable exc) {
			return Throwables.rethrow(exc);
		}
	}

	@Override
	public void clear() {
		utility.delete(new File(basePath), false);
		logger.info("Physical cache cleaning done");
	}

}
