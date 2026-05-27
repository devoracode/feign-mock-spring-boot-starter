package io.github.feignmock.exception;

/**
 * Mock 数据相关异常。
 *
 * <p>在以下情况下抛出：
 * <ul>
 *   <li>指定 key 在所有数据源中均未找到</li>
 *   <li>指定 JSON 文件不存在</li>
 *   <li>JSON 反序列化失败</li>
 *   <li>{@link io.github.feignmock.provider.MockDataProvider} 无法实例化</li>
 * </ul>
 *
 * @author Wenjie
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
