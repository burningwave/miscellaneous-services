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

import java.io.Serializable;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public interface SimpleCache {

	public <T extends Serializable> void store(String key, T object);

	public <T extends Serializable> void storeAndNotify(String key, T newValue, T oldValue);

	public <T extends Serializable> T load(String key);

	public void delete(String... keys);

	public void clear();

	public Set<Listener> getListeners();

	@lombok.NoArgsConstructor
	@lombok.Getter
	@lombok.Setter
	@lombok.ToString
	public static class Item<T extends Serializable> implements Serializable {

		private static final long serialVersionUID = -1321130683026508947L;

		private Date time;
		private T value;

	}

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
