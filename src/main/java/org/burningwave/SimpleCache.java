package org.burningwave;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public interface SimpleCache {

	public <T extends Serializable> void store(String key, T object);

	public <T extends Serializable> void storeAndNotify(String key, T newValue, T oldValue);

	public <T extends Serializable> T load(String key);

	public void clear();

	public Set<Listener> getListeners();

	public static abstract class Abst implements SimpleCache {
		private final static org.slf4j.Logger logger;
		private Set<Abst.Listener> listeners;

	    static {
	    	logger = org.slf4j.LoggerFactory.getLogger(Abst.class);
	    }

		@Override
		public Set<Listener> getListeners() {
			if (listeners == null) {
				synchronized (this) {
					if (listeners == null) {
						listeners = ConcurrentHashMap.newKeySet();
					}
				}
			}
			return listeners;
		}

		@Override
		public <T extends Serializable> void storeAndNotify(String key, T newValue, T oldValue) {
			store(key, newValue);
			notifyChange(key, newValue, oldValue);
		}

		private <T extends Serializable> void notifyChange(String key, T newValue, T oldValue) {
			for (Listener listener : getListeners()) {
				try  {
					CompletableFuture.runAsync(() ->
						listener.processChangeNotification(key, newValue, oldValue)
					);
				} catch (Throwable exc){
					logger.error("Exception occurred while notifying the storing of {} - {} to {}", key, newValue, listener, exc);
				}
			}
		}
	}

	public static interface Listener {

		@SuppressWarnings("unchecked")
		public default <L extends Abst.Listener> L listenTo(SimpleCache cache) {
			cache.getListeners().add(this);
			return (L)this;
		}

		public <T extends Serializable> void processChangeNotification(String key, T newValue, T oldValue);

	}

}
