package org.burningwave.services;

public class NoResultException extends RuntimeException {

	private static final long serialVersionUID = 4778628874139863801L;

	public NoResultException(String message) {
		super(message);
	}
}