package io.github.devoracode.feignmock.aspect;

import io.github.devoracode.feignmock.TestFixtures.DynamicUserProvider;
import io.github.devoracode.feignmock.TestFixtures.UserDTO;
import io.github.devoracode.feignmock.TestFixtures.UserFeignClient;
import io.github.devoracode.feignmock.annotation.MockMethod;
import io.github.devoracode.feignmock.autoconfigure.FeignMockAutoConfiguration;
import io.github.devoracode.feignmock.autoconfigure.FeignMockProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = { FeignMockAutoConfiguration.class, FeignMockMethodAspectTest.TestConfig.class })
@TestPropertySource(properties = "feign.mock.enabled=true")
@DisplayName("FeignMockMethodAspect")
class FeignMockMethodAspectTest {

	@Autowired
	private UserFeignClient userFeignClient;

	@Autowired
	private FeignMockProperties properties;

	@Test
	@DisplayName("resolves auto-derived key from configuration")
	void autoKeyLocalFile() {
		this.properties.getResponses().put("userFeignClient.getUserById",
				"{\"userId\":1,\"username\":\"auto_key_user\",\"status\":\"AUTO\"}");

		UserDTO user = this.userFeignClient.getUserById(1L);

		assertThat(user.getUsername()).isEqualTo("auto_key_user");
	}

	@Test
	@DisplayName("resolves manually configured key")
	void manualKeyNacos() {
		this.properties.getResponses().put("userClient.listUsers",
				"[{\"userId\":10,\"username\":\"list_user\"}]");

		List<UserDTO> users = this.userFeignClient.listUsers(1, 10);

		assertThat(users).hasSize(1);
		assertThat(users.get(0).getUsername()).isEqualTo("list_user");
	}

	@Test
	@DisplayName("loads mock response from jsonFile")
	void jsonFileSource() {
		UserDTO user = this.userFeignClient.getUserByFile(1L);

		assertThat(user).isNotNull();
		assertThat(user.getUsername()).isEqualTo("file_mock_user");
		assertThat(user.getStatus()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("loads mock response from provider")
	void providerDynamicResponse() {
		UserDTO user1 = this.userFeignClient.getUserByProvider(42L);
		UserDTO user2 = this.userFeignClient.getUserByProvider(100L);

		assertThat(user1.getUsername()).isEqualTo("provider_user_42");
		assertThat(user2.getUsername()).isEqualTo("provider_user_100");
		assertThat(user1.getStatus()).isEqualTo("PROVIDER");
	}

	@Test
	@DisplayName("returns null when failFast is false")
	void failFastFalseReturnsNull() {
		UserDTO result = this.userFeignClient.getUserSilent(1L);

		assertThat(result).isNull();
	}

	@Test
	@DisplayName("proceeds normally when the global switch is disabled")
	void globalSwitchDisabledProceedNormally() {
		this.properties.setEnabled(false);
		try {
			assertThatThrownBy(() -> this.userFeignClient.getUserById(1L))
					.isInstanceOf(UnsupportedOperationException.class)
					.hasMessage("real feign call");
		}
		finally {
			this.properties.setEnabled(true);
		}
	}

	@Configuration
	@EnableAspectJAutoProxy
	static class TestConfig {

		@Bean
		DynamicUserProvider dynamicUserProvider() {
			return new DynamicUserProvider();
		}

		@Bean
		UserFeignClient userFeignClient() {
			return new UserFeignClient() {

				@Override
				@MockMethod(description = "auto key")
				public UserDTO getUserById(Long userId) {
					throw new UnsupportedOperationException("real feign call");
				}

				@Override
				@MockMethod(value = "userClient.listUsers", description = "manual key")
				public List<UserDTO> listUsers(Integer page, Integer size) {
					throw new UnsupportedOperationException("real feign call");
				}

				@Override
				@MockMethod(jsonFile = "mock/userClient/getUserById.json", description = "json file")
				public UserDTO getUserByFile(Long userId) {
					throw new UnsupportedOperationException("real feign call");
				}

				@Override
				@MockMethod(provider = DynamicUserProvider.class, description = "provider")
				public UserDTO getUserByProvider(Long userId) {
					throw new UnsupportedOperationException("real feign call");
				}

				@Override
				@MockMethod(value = "not.exist.key", failFast = false, description = "fail fast false")
				public UserDTO getUserSilent(Long userId) {
					throw new UnsupportedOperationException("real feign call");
				}

			};
		}

	}

}
