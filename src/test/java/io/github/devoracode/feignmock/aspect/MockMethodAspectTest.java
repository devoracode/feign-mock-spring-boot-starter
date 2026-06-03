package io.github.devoracode.feignmock.aspect;

import io.github.devoracode.feignmock.TestFixtures.DynamicUserProvider;
import io.github.devoracode.feignmock.TestFixtures.UserDTO;
import io.github.devoracode.feignmock.TestFixtures.UserFeignClient;
import io.github.devoracode.feignmock.annotation.MockMethod;
import io.github.devoracode.feignmock.config.MockAutoConfiguration;
import io.github.devoracode.feignmock.config.MockProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link MockMethodAspect} 集成测试，验证三种数据源的完整拦截链路。
 */
@SpringBootTest(classes = {
    MockAutoConfiguration.class,
    MockMethodAspectTest.TestConfig.class
})
@TestPropertySource(properties = "feign.mock.enabled=true")
@DisplayName("MockMethodAspect 集成测试")
class MockMethodAspectTest {

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private MockProperties mockProperties;

    // ── 数据源一：key 查找（本地文件） ────────────────────────────────────────

    @Test
    @DisplayName("key 自动推导，配置文件命中")
    void autoKey_localFile() {
        // UserFeignClient → 首字母小写 → autoKey = "userFeignClient.getUserById"
        mockProperties.getResponses().put(
            "userFeignClient.getUserById",
            "{\"userId\":1,\"username\":\"auto_key_user\",\"status\":\"AUTO\"}"
        );

        UserDTO user = userFeignClient.getUserById(1L);

        assertThat(user.getUsername()).isEqualTo("auto_key_user");
    }

    @Test
    @DisplayName("手动指定 key，Nacos（MockProperties）命中")
    void manualKey_nacos() {
        mockProperties.getResponses().put(
            "userClient.listUsers",
            "[{\"userId\":10,\"username\":\"list_user\"}]"
        );

        List<UserDTO> users = userFeignClient.listUsers(1, 10);

        assertThat(users).hasSize(1);
        assertThat(users.get(0).getUsername()).isEqualTo("list_user");
    }

    // ── 数据源二：指定 JSON 文件 ──────────────────────────────────────────────

    @Test
    @DisplayName("jsonFile 方式，直接读取本地 JSON 文件")
    void jsonFile_source() {
        UserDTO user = userFeignClient.getUserByFile(1L);

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("file_mock_user");
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    // ── 数据源三：自定义 Provider ─────────────────────────────────────────────

    @Test
    @DisplayName("Provider 方式，根据入参动态返回数据")
    void provider_dynamicResponse() {
        UserDTO user1 = userFeignClient.getUserByProvider(42L);
        UserDTO user2 = userFeignClient.getUserByProvider(100L);

        assertThat(user1.getUsername()).isEqualTo("provider_user_42");
        assertThat(user2.getUsername()).isEqualTo("provider_user_100");
        assertThat(user1.getStatus()).isEqualTo("PROVIDER");
    }

    // ── failFast=false ────────────────────────────────────────────────────────

    @Test
    @DisplayName("failFast=false，key 不存在时返回 null 而非抛异常")
    void failFastFalse_returnsNull() {
        UserDTO result = userFeignClient.getUserSilent(1L);

        assertThat(result).isNull();
    }

    // ── 全局开关 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("全局开关 disabled 时，切面直接放行（调用真实实现）")
    void globalSwitch_disabled_proceedNormally() {
        mockProperties.setEnabled(false);
        try {
            // 真实实现抛 UnsupportedOperationException（测试桩）
            assertThatThrownBy(() -> userFeignClient.getUserById(1L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("real feign call");
        } finally {
            mockProperties.setEnabled(true);
        }
    }

    // ── Test Configuration ────────────────────────────────────────────────────

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        /** 注册 Provider 为 Spring Bean，验证 MockProviderRegistry 走容器路径 */
        @Bean
        public DynamicUserProvider dynamicUserProvider() {
            return new DynamicUserProvider();
        }

        /** 测试用 UserFeignClient 实现（真实方法抛异常，模拟 Feign 代理） */
        @Bean
        public UserFeignClient userFeignClient() {
            return new UserFeignClient() {
                @Override
                @MockMethod(description = "自动推导 key")
                public UserDTO getUserById(Long userId) {
                    throw new UnsupportedOperationException("real feign call");
                }

                @Override
                @MockMethod(value = "userClient.listUsers", description = "手动指定 key")
                public List<UserDTO> listUsers(Integer page, Integer size) {
                    throw new UnsupportedOperationException("real feign call");
                }

                @Override
                @MockMethod(jsonFile = "mock/userClient/getUserById.json", description = "指定文件")
                public UserDTO getUserByFile(Long userId) {
                    throw new UnsupportedOperationException("real feign call");
                }

                @Override
                @MockMethod(provider = DynamicUserProvider.class, description = "自定义 Provider")
                public UserDTO getUserByProvider(Long userId) {
                    throw new UnsupportedOperationException("real feign call");
                }

                @Override
                @MockMethod(value = "not.exist.key", failFast = false, description = "failFast=false")
                public UserDTO getUserSilent(Long userId) {
                    throw new UnsupportedOperationException("real feign call");
                }
            };
        }
    }
}
