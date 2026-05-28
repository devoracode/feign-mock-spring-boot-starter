package io.github.feignmock.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.feignmock.TestFixtures.UserDTO;
import io.github.feignmock.config.MockProperties;
import io.github.feignmock.exception.MockDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MockDataLoader 单元测试")
class MockDataLoaderTest {

    private MockDataLoader loader;
    private MockProperties mockProperties;
    private LocalFileMockDataSource localFileMockDataSource;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .registerModule(new JavaTimeModule());

        mockProperties = new MockProperties();
        mockProperties.setEnabled(true);

        localFileMockDataSource = new LocalFileMockDataSource(new DefaultResourceLoader());
        List<MockDataSource> sources = Collections.singletonList(localFileMockDataSource);
        loader = new MockDataLoader(objectMapper, sources);
    }

    // ── loadByKey ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadByKey：本地文件命中，正确反序列化单个对象")
    void loadByKey_localFile_singleObject() {
        UserDTO user = loader.loadByKey("userClient.getUserById", UserDTO.class);

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("file_mock_user");
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("loadByKey：本地文件命中，正确反序列化 List 泛型")
    void loadByKey_localFile_listGeneric() throws Exception {
        java.lang.reflect.Type listType =
            new com.fasterxml.jackson.core.type.TypeReference<List<UserDTO>>() {}.getType();

        List<UserDTO> users = loader.loadByKey("userClient.listUsers", listType);

        assertThat(users).hasSize(2);
        assertThat(users.get(0).getUsername()).isEqualTo("user_one");
        assertThat(users.get(1).getUsername()).isEqualTo("user_two");
    }

    @Test
    @DisplayName("loadByKey：key 不存在时抛出 MockDataException")
    void loadByKey_notFound_throwsException() {
        assertThatThrownBy(() -> loader.loadByKey("not.exist.key", UserDTO.class))
            .isInstanceOf(MockDataException.class)
            .hasMessageContaining("not.exist.key");
    }

    // ── loadByFile ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadByFile：指定路径正确加载")
    void loadByFile_success() {
        UserDTO user = loader.loadByFile(
            "mock/userClient/getUserById.json", UserDTO.class);

        assertThat(user.getUsername()).isEqualTo("file_mock_user");
    }

    @Test
    @DisplayName("loadByFile：文件不存在时抛出 MockDataException")
    void loadByFile_notFound_throwsException() {
        assertThatThrownBy(() -> loader.loadByFile("mock/not/exist.json", UserDTO.class))
            .isInstanceOf(MockDataException.class)
            .hasMessageContaining("Mock file not found");
    }

    // ── findJsonByKey ────────────────────────────────────────────────────────

    @Test
    @DisplayName("findJsonByKey：所有数据源均无时返回 empty")
    void findJsonByKey_allMiss_returnsEmpty() {
        assertThat(loader.findJsonByKey("absolutely.not.exist")).isEmpty();
    }
}
