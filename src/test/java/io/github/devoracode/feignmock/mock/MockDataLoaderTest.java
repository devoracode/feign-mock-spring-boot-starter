package io.github.devoracode.feignmock.mock;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.devoracode.feignmock.TestFixtures.UserDTO;
import io.github.devoracode.feignmock.exception.MockDataException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.DefaultResourceLoader;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MockDataLoader")
class MockDataLoaderTest {

	private MockDataLoader loader;

	@BeforeEach
	void setUp() {
		ObjectMapper objectMapper = new ObjectMapper()
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.registerModule(new JavaTimeModule());

		ResourceMockDataSource resourceMockDataSource = new ResourceMockDataSource(new DefaultResourceLoader());
		List<MockDataSource> sources = Collections.singletonList(resourceMockDataSource);
		this.loader = new MockDataLoader(objectMapper, sources);
	}

	@Test
	@DisplayName("loadByKey resolves a single object from classpath resources")
	void loadByKeyLocalFileSingleObject() {
		UserDTO user = this.loader.loadByKey("userClient.getUserById", UserDTO.class);

		assertThat(user).isNotNull();
		assertThat(user.getUsername()).isEqualTo("file_mock_user");
		assertThat(user.getStatus()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("loadByKey resolves generic list responses")
	void loadByKeyLocalFileListGeneric() throws Exception {
		java.lang.reflect.Type listType = new com.fasterxml.jackson.core.type.TypeReference<List<UserDTO>>() {
		}.getType();

		List<UserDTO> users = this.loader.loadByKey("userClient.listUsers", listType);

		assertThat(users).hasSize(2);
		assertThat(users.get(0).getUsername()).isEqualTo("user_one");
		assertThat(users.get(1).getUsername()).isEqualTo("user_two");
	}

	@Test
	@DisplayName("loadByKey throws when the key cannot be resolved")
	void loadByKeyNotFoundThrowsException() {
		assertThatThrownBy(() -> this.loader.loadByKey("not.exist.key", UserDTO.class))
				.isInstanceOf(MockDataException.class)
				.hasMessageContaining("not.exist.key");
	}

	@Test
	@DisplayName("loadByFile resolves a resource by path")
	void loadByFileSuccess() {
		UserDTO user = this.loader.loadByFile("mock/userClient/getUserById.json", UserDTO.class);

		assertThat(user.getUsername()).isEqualTo("file_mock_user");
	}

	@Test
	@DisplayName("loadByFile throws when the resource does not exist")
	void loadByFileNotFoundThrowsException() {
		assertThatThrownBy(() -> this.loader.loadByFile("mock/not/exist.json", UserDTO.class))
				.isInstanceOf(MockDataException.class)
				.hasMessageContaining("Mock file not found");
	}

	@Test
	@DisplayName("findJsonByKey returns empty when no source matches")
	void findJsonByKeyAllMissReturnsEmpty() {
		assertThat(this.loader.findJsonByKey("absolutely.not.exist")).isEmpty();
	}

}
