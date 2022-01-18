package org.burningwave;

import java.io.Serializable;

public interface SimpleCache {

	public void store(String key, Serializable object);

	public <T extends Serializable> T load(String key);

	public void clear();

}
