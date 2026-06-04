package io.github.devoracode.feignmock.el;

import io.github.devoracode.feignmock.TestFixtures.UserDTO;
import io.github.devoracode.feignmock.annotation.MockMethod;
import io.github.devoracode.feignmock.autoconfigure.FeignMockAutoConfiguration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = { FeignMockAutoConfiguration.class, MockElIntegrationTest.TestConfig.class })
@TestPropertySource(properties = "feign.mock.enabled=true")
@DisplayName("Mock expression integration")
class MockElIntegrationTest {

	@Autowired
	private ElFeignClient elFeignClient;

	@Test
	@DisplayName("resolves #switch case A")
	void elSwitchStringMatchA() {
		UserDTO user = this.elFeignClient.getByType("A");
		assertThat(user.getStatus()).isEqualTo("TYPE_A");
	}

	@Test
	@DisplayName("resolves #switch case B")
	void elSwitchStringMatchB() {
		UserDTO user = this.elFeignClient.getByType("B");
		assertThat(user.getStatus()).isEqualTo("TYPE_B");
	}

	@Test
	@DisplayName("resolves #switch default case")
	void elSwitchStringDefault() {
		UserDTO user = this.elFeignClient.getByType("C");
		assertThat(user.getStatus()).isEqualTo("DEFAULT");
	}

	@Test
	@DisplayName("resolves #switch with map parameter")
	void elSwitchMapField() {
		Map<String, Object> request = new HashMap<>();
		request.put("userType", "A");
		UserDTO user = this.elFeignClient.getByMap(request);
		assertThat(user.getStatus()).isEqualTo("TYPE_A");
	}

	@Test
	@DisplayName("resolves #switch with Java object parameter")
	void elSwitchJavaObject() {
		UserDTO user = this.elFeignClient.getByRequest(new UserRequest("B"));
		assertThat(user.getStatus()).isEqualTo("TYPE_B");
	}

	interface ElFeignClient {

		UserDTO getByType(String type);

		UserDTO getByMap(Map<String, Object> req);

		UserDTO getByRequest(UserRequest req);

	}

	static class UserRequest {

		private final String userType;

		UserRequest(String userType) {
			this.userType = userType;
		}

		public String getUserType() {
			return this.userType;
		}

	}

	@Configuration
	@EnableAspectJAutoProxy
	static class TestConfig {

		@Bean
		ElFeignClient elFeignClient() {
			return new ElFeignClient() {

				@Override
				@MockMethod(jsonFile = "#switch(#type,'A','mock/el/type_a.json','B','mock/el/type_b.json','mock/el/default.json')")
				public UserDTO getByType(String type) {
					throw new UnsupportedOperationException("real feign call");
				}

				@Override
				@MockMethod(jsonFile = "#switch(#req.userType,'A','mock/el/type_a.json','mock/el/default.json')")
				public UserDTO getByMap(Map<String, Object> req) {
					throw new UnsupportedOperationException("real feign call");
				}

				@Override
				@MockMethod(jsonFile = "#switch(#req.userType,'A','mock/el/type_a.json','B','mock/el/type_b.json','mock/el/default.json')")
				public UserDTO getByRequest(UserRequest req) {
					throw new UnsupportedOperationException("real feign call");
				}

			};
		}

	}

}
