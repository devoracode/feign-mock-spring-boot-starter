package io.github.devoracode.feignmock.el;

import io.github.devoracode.feignmock.TestFixtures.UserDTO;
import io.github.devoracode.feignmock.annotation.MockMethod;
import io.github.devoracode.feignmock.config.MockAutoConfiguration;
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

/**
 * EL 表达式在 AOP 完整链路中的集成测试。
 */
@SpringBootTest(classes = {
    MockAutoConfiguration.class,
    MockElIntegrationTest.TestConfig.class
})
@TestPropertySource(properties = "feign.mock.enabled=true")
@DisplayName("MockEL 集成测试")
class MockElIntegrationTest {

    @Autowired
    private ElFeignClient elFeignClient;

    @Test
    @DisplayName("String 参数 #switch，命中 A 返回 type_a.json")
    void el_switch_string_matchA() {
        UserDTO user = elFeignClient.getByType("A");
        assertThat(user.getStatus()).isEqualTo("TYPE_A");
    }

    @Test
    @DisplayName("String 参数 #switch，命中 B 返回 type_b.json")
    void el_switch_string_matchB() {
        UserDTO user = elFeignClient.getByType("B");
        assertThat(user.getStatus()).isEqualTo("TYPE_B");
    }

    @Test
    @DisplayName("String 参数 #switch，无匹配返回 default.json")
    void el_switch_string_default() {
        UserDTO user = elFeignClient.getByType("C");
        assertThat(user.getStatus()).isEqualTo("DEFAULT");
    }

    @Test
    @DisplayName("Map 参数，取嵌套字段 #switch")
    void el_switch_map_field() {
        Map<String, Object> req = new HashMap<>();
        req.put("userType", "A");
        UserDTO user = elFeignClient.getByMap(req);
        assertThat(user.getStatus()).isEqualTo("TYPE_A");
    }

    @Test
    @DisplayName("Java 对象参数，取字段 #switch")
    void el_switch_java_object() {
        UserDTO user = elFeignClient.getByRequest(new UserRequest("B"));
        assertThat(user.getStatus()).isEqualTo("TYPE_B");
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    interface ElFeignClient {
        UserDTO getByType(String type);
        UserDTO getByMap(Map<String, Object> req);
        UserDTO getByRequest(UserRequest req);
    }

    static class UserRequest {
        private final String userType;
        UserRequest(String userType) { this.userType = userType; }
        public String getUserType() { return userType; }
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {
        @Bean
        public ElFeignClient elFeignClient() {
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
