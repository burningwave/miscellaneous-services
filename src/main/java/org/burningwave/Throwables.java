package org.burningwave;

public class Throwables {

	@SuppressWarnings("unchecked")
	public static <T, E extends Throwable> T rethrow(Throwable exc) throws E {
		throw (E)exc;
	}

}
