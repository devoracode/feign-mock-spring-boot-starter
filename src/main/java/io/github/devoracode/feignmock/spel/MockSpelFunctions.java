package io.github.devoracode.feignmock.spel;

import java.lang.reflect.Array;
import java.util.Collection;

import io.github.devoracode.feignmock.exception.MockDataException;

/**
 * SpEL helper functions exposed to mock response expressions.
 *
 * @author Wenjie Liu
 * @since 1.1.0
 */
public final class MockSpelFunctions {

	private MockSpelFunctions() {
	}

	public static String switchOf(Object value, Object... args) {
		String key = value == null ? "" : String.valueOf(value);
		int index = 0;
		while (index + 1 < args.length) {
			String caseValue = args[index] == null ? "" : String.valueOf(args[index]);
			String filePath = args[index + 1] == null ? null : String.valueOf(args[index + 1]);
			if (caseValue.equals(key)) {
				return filePath;
			}
			index += 2;
		}
		if (index < args.length) {
			Object defaultValue = args[index];
			return defaultValue == null ? null : String.valueOf(defaultValue);
		}
		throw new MockDataException("No matching case in #switch for value: [" + key + "], and no default provided.");
	}

	public static boolean containsOf(Object target, Object needle) {
		if (target == null) {
			return false;
		}
		String needleValue = needle == null ? null : String.valueOf(needle);
		if (target instanceof String) {
			return needleValue != null && ((String) target).contains(needleValue);
		}
		if (target instanceof Collection) {
			Collection<?> collection = (Collection<?>) target;
			if (collection.contains(needle)) {
				return true;
			}
			if (needleValue == null) {
				return false;
			}
			for (Object value : collection) {
				if (needleValue.equals(String.valueOf(value))) {
					return true;
				}
			}
			return false;
		}
		Class<?> type = target.getClass();
		if (type.isArray()) {
			int length = Array.getLength(target);
			for (int i = 0; i < length; i++) {
				Object value = Array.get(target, i);
				if (needle == null) {
					if (value == null) {
						return true;
					}
				}
				else if (needle.equals(value) || String.valueOf(needle).equals(String.valueOf(value))) {
					return true;
				}
			}
			return false;
		}
		return false;
	}

	public static String chooseOf(Object... args) {
		if (args == null || args.length == 0) {
			throw new MockDataException(
					"#choose requires arguments: (cond1, file1, cond2, file2, ..., defaultFile)");
		}
		int index = 0;
		while (index + 1 < args.length) {
			if (isTruthy(args[index])) {
				Object file = args[index + 1];
				return file == null ? null : String.valueOf(file);
			}
			index += 2;
		}
		if (index < args.length) {
			Object defaultValue = args[index];
			return defaultValue == null ? null : String.valueOf(defaultValue);
		}
		throw new MockDataException("No condition matched in #choose, and no default provided.");
	}

	private static boolean isTruthy(Object value) {
		if (value == null) {
			return false;
		}
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		if (value instanceof Number) {
			return ((Number) value).doubleValue() != 0D;
		}
		String stringValue = String.valueOf(value).trim();
		return "true".equalsIgnoreCase(stringValue);
	}

}
