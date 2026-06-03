package io.github.devoracode.feignmock;

import io.github.devoracode.feignmock.annotation.MockMethod;
import io.github.devoracode.feignmock.provider.MockDataProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 测试用共享类型，集中定义避免重复。
 */
public final class TestFixtures {

    private TestFixtures() {}

    // ── DTO ──────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDTO {
        private Long userId;
        private String username;
        private String email;
        private String status;
    }

    // ── Feign 客户端接口（测试用） ──────────────────────────────────────────

    public interface UserFeignClient {

        /** key 自动推导：userFeignClient → user → userFeignClient... 实际推导为 user.getUserById */
        @MockMethod(description = "自动推导 key")
        UserDTO getUserById(Long userId);

        @MockMethod(value = "userClient.listUsers", description = "手动指定 key")
        List<UserDTO> listUsers(Integer page, Integer size);

        @MockMethod(jsonFile = "mock/userClient/getUserById.json", description = "指定文件")
        UserDTO getUserByFile(Long userId);

        @MockMethod(provider = DynamicUserProvider.class, description = "自定义 Provider")
        UserDTO getUserByProvider(Long userId);

        @MockMethod(value = "not.exist.key", failFast = false, description = "failFast=false")
        UserDTO getUserSilent(Long userId);
    }

    // ── 自定义 Provider ──────────────────────────────────────────────────────

    @Component
    public static class DynamicUserProvider implements MockDataProvider {
        @Override
        public Object provide(ProceedingJoinPoint pjp, Method method) {
            Long userId = (Long) pjp.getArgs()[0];
            return UserDTO.builder()
                .userId(userId)
                .username("provider_user_" + userId)
                .status("PROVIDER")
                .build();
        }
    }
}
