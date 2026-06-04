package io.github.devoracode.feignmock.exception;

import io.github.devoracode.feignmock.provider.MockDataProvider;

/**
 * Exception thrown when mock data cannot be resolved or deserialized.
 *
 * @author Wenjie Liu
 * @since 1.0.0
 */
public class MockDataException extends RuntimeException {

	public MockDataException(String message) {
		super(message);
	}

	public MockDataException(String message, Throwable cause) {
		super(message, cause);
	}

}
