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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class DBBasedCache implements SimpleCache {
	private final static org.slf4j.Logger logger;

	@Autowired
	private Item.Repository repository;

	@Autowired
	private Utility utility;

    static {
    	logger = org.slf4j.LoggerFactory.getLogger(DBBasedCache.class);
    }

	public DBBasedCache(Map<String, Object> configMap) {}

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
	}

	@Override
	public <T extends Serializable> T load(String key) {
		Item cacheItem = repository.findByKey(key);
		if (cacheItem != null) {
			try {
				return utility.deserialize(cacheItem.getValue());
			} catch (IOException | ClassNotFoundException exc) {
				Throwables.rethrow(exc);
			}
		}
		return null;
	}

	@Override
	public void clear() {
		repository.deleteAll();
		logger.info("Physical cache cleaning done");
	}

	@Entity
	@Table(name = "CacheItem")
	@Getter @Setter @NoArgsConstructor
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

		}

	}
}
