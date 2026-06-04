package io.github.devoracode.feignmock.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.devoracode.feignmock.aspect.FeignMockMethodAspect;
import io.github.devoracode.feignmock.mock.MockDataLoader;
import io.github.devoracode.feignmock.mock.MockDataSource;
import io.github.devoracode.feignmock.mock.PropertiesMockDataSource;
import io.github.devoracode.feignmock.mock.ResourceMockDataSource;
import io.github.devoracode.feignmock.provider.MockDataProviderRegistry;
import io.github.devoracode.feignmock.spel.MockExpressionEvaluator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} for
 * annotation-driven Feign mock responses.
 *
 * @author Wenjie Liu
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = FeignMockProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(FeignMockProperties.class)
public class FeignMockAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(FeignMockAutoConfiguration.class);

	public static final String MOCK_OBJECT_MAPPER_BEAN_NAME = "feignMockObjectMapper";

	@Bean(name = MOCK_OBJECT_MAPPER_BEAN_NAME)
	@ConditionalOnMissingBean(name = MOCK_OBJECT_MAPPER_BEAN_NAME)
	ObjectMapper feignMockObjectMapper() {
		return new ObjectMapper()
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
				.registerModule(new JavaTimeModule());
	}

	@Bean
	@ConditionalOnMissingBean
	PropertiesMockDataSource propertiesMockDataSource(FeignMockProperties properties) {
		return new PropertiesMockDataSource(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	ResourceMockDataSource resourceMockDataSource(ResourceLoader resourceLoader) {
		return new ResourceMockDataSource(resourceLoader);
	}

	@Bean
	@ConditionalOnMissingBean
	MockDataLoader mockDataLoader(
			@Qualifier(MOCK_OBJECT_MAPPER_BEAN_NAME) ObjectMapper feignMockObjectMapper,
			ObjectProvider<MockDataSource> dataSources) {
		List<MockDataSource> sources = new ArrayList<>();
		dataSources.orderedStream().forEach(sources::add);
		logger.info("Registered {} Feign mock data source(s)", sources.size());
		return new MockDataLoader(feignMockObjectMapper, sources);
	}

	@Bean
	@ConditionalOnMissingBean
	MockDataProviderRegistry mockDataProviderRegistry() {
		return new MockDataProviderRegistry();
	}

	@Bean
	@ConditionalOnMissingBean
	MockExpressionEvaluator mockExpressionEvaluator() {
		return new MockExpressionEvaluator();
	}

	@Bean
	@ConditionalOnMissingBean
	FeignMockMethodAspect feignMockMethodAspect(MockDataLoader mockDataLoader,
			MockDataProviderRegistry mockDataProviderRegistry,
			MockExpressionEvaluator mockExpressionEvaluator, FeignMockProperties properties) {
		logger.info("Feign mock support is enabled");
		return new FeignMockMethodAspect(mockDataLoader, mockDataProviderRegistry,
				mockExpressionEvaluator, properties);
	}

}
