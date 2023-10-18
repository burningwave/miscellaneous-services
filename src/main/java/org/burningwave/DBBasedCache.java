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
 * Copyright (c) 2022-2023 Roberto Gentili
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

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

public class DBBasedCache extends SimpleCache.Abst {
	private final static org.slf4j.Logger logger;

	@Autowired
	private Item.Repository repository;

	@Autowired
	private Utility utility;

    static {
    	logger = org.slf4j.LoggerFactory.getLogger(DBBasedCache.class);
    }

	public DBBasedCache(Map<String, Object> configMap) {
		logger.info("Database based cache successfully instantiated");
	}

	@Override
	public void store(String key, Serializable object) {
		Item cacheItem = repository.findByKey(key);
		if (cacheItem == null) {
			cacheItem = new Item();
			cacheItem.setKey(key);
		}
		try {
			cacheItem.setValue(utility.serialize(object));
		} catch (IOException exc) {
			Throwables.rethrow(exc);
		}
		repository.save(cacheItem);
		logger.info("Object with id '{}' stored in the physical cache", key);
	}

	@Override
	public <T extends Serializable> T load(String key) {
		Item cacheItem = repository.findByKey(key);
		if (cacheItem != null) {
			try {
				T effectiveItem = utility.deserialize(cacheItem.getValue());
				logger.info("Object with id '{}' loaded from physical cache: {}", key, effectiveItem);
				return effectiveItem;
			} catch (IOException | ClassNotFoundException exc) {
				Throwables.rethrow(exc);
			}
		}
		return null;
	}

	@Override
	@Transactional
	public void delete(String... keys) {
		for (String key : keys) {
			repository.deleteByKey(key);
		}
	}

	@Override
	public void clear() {
		repository.deleteAll();
		logger.info("Physical cache cleaning done");
	}

	@Entity
	@Table(name = "CacheItem")
	@NoArgsConstructor
	@Getter
	@Setter
	@ToString
	public static class Item implements Serializable {

		private static final long serialVersionUID = 3154577811258150600L;

		@Id
		@GeneratedValue(strategy = GenerationType.AUTO)
		private long id;

		@Column(name = "key", length = 256)
		private String key;

		@Column(name = "value")
		private byte[] value;

		@org.springframework.stereotype.Repository
		public static interface Repository extends JpaRepository<Item, String>  {

			public Item findByKey(String key);

			public void deleteByKey(String key);

		}

	}

}
