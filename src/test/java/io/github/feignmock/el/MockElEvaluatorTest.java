package io.github.feignmock.el;

import io.github.feignmock.exception.MockDataException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("MockElEvaluator 单元测试")
class MockElEvaluatorTest {

    private MockElEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new MockElEvaluator();
    }

    // ── isExpression ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("isExpression: 以 # 开头返回 true")
    void isExpression_true() {
        assertThat(MockElEvaluator.isExpression("#switch(#type,'A','a.json')")).isTrue();
    }

    @Test
    @DisplayName("isExpression: 普通路径返回 false")
    void isExpression_false() {
        assertThat(MockElEvaluator.isExpression("mock/user.json")).isFalse();
        assertThat(MockElEvaluator.isExpression("")).isFalse();
        assertThat(MockElEvaluator.isExpression(null)).isFalse();
    }

    // ── #switch with String param ─────────────────────────────────────────────

    @Nested
    @DisplayName("#switch 表达式")
    class SwitchTests {

        @Test
        @DisplayName("String 参数，命中 case A")
        void switch_stringParam_matchA() {
            ProceedingJoinPoint pjp = mockPjp(new Object[]{"A"}, new String[]{"type"});
            String result = evaluator.evaluate("#switch(#type,'A','mock/a.json','B','mock/b.json','mock/d.json')", pjp);
            assertThat(result).isEqualTo("mock/a.json");
        }

        @Test
        @DisplayName("String 参数，命中 case B")
        void switch_stringParam_matchB() {
            ProceedingJoinPoint pjp = mockPjp(new Object[]{"B"}, new String[]{"type"});
            String result = evaluator.evaluate("#switch(#type,'A','mock/a.json','B','mock/b.json','mock/d.json')", pjp);
            assertThat(result).isEqualTo("mock/b.json");
        }

        @Test
        @DisplayName("String 参数，无匹配走默认")
        void switch_stringParam_default() {
            ProceedingJoinPoint pjp = mockPjp(new Object[]{"C"}, new String[]{"type"});
            String result = evaluator.evaluate("#switch(#type,'A','mock/a.json','B','mock/b.json','mock/d.json')", pjp);
            assertThat(result).isEqualTo("mock/d.json");
        }

        @Test
        @DisplayName("按索引 #p0 引用参数")
        void switch_indexParam() {
            ProceedingJoinPoint pjp = mockPjp(new Object[]{"A"}, new String[]{"type"});
            String result = evaluator.evaluate("#switch(#p0,'A','mock/a.json','mock/d.json')", pjp);
            assertThat(result).isEqualTo("mock/a.json");
        }

        @Test
        @DisplayName("无默认值且无匹配时抛出异常")
        void switch_noDefault_noMatch_throws() {
            ProceedingJoinPoint pjp = mockPjp(new Object[]{"C"}, new String[]{"type"});
            assertThatThrownBy(() ->
                evaluator.evaluate("#switch(#type,'A','mock/a.json','B','mock/b.json')", pjp))
                .isInstanceOf(MockDataException.class)
                .hasMessageContaining("No matching case");
        }

        @Test
        @DisplayName("Map 参数，取 key 值匹配")
        void switch_mapParam() {
            Map<String, Object> req = new HashMap<>();
            req.put("userType", "A");
            ProceedingJoinPoint pjp = mockPjp(new Object[]{req}, new String[]{"req"});
            String result = evaluator.evaluate("#switch(#req.userType,'A','mock/a.json','mock/d.json')", pjp);
            assertThat(result).isEqualTo("mock/a.json");
        }

        @Test
        @DisplayName("Java 对象参数，取字段值匹配")
        void switch_javaObjectParam() {
            TestRequest req = new TestRequest("B");
            ProceedingJoinPoint pjp = mockPjp(new Object[]{req}, new String[]{"req"});
            String result = evaluator.evaluate("#switch(#req.userType,'A','mock/a.json','B','mock/b.json','mock/d.json')", pjp);
            assertThat(result).isEqualTo("mock/b.json");
        }

        @Test
        @DisplayName("Map 参数，集合 contains 判断返回 true/false")
        void switch_mapCollection_contains() {
            Map<String, Object> req = new HashMap<>();
            req.put("types", Arrays.asList("A", "B"));
            ProceedingJoinPoint pjp = mockPjp(new Object[]{req}, new String[]{"req"});

            String resultTrue = evaluator.evaluate(
                "#switch(#req.types.contains('A'),'true','mock/a.json','mock/d.json')", pjp);
            String resultFalse = evaluator.evaluate(
                "#switch(#req.types.contains('C'),'true','mock/a.json','mock/d.json')", pjp);

            assertThat(resultTrue).isEqualTo("mock/a.json");
            assertThat(resultFalse).isEqualTo("mock/d.json");
        }

        @Test
        @DisplayName("Map 参数，数组 contains 判断（使用 #contains 函数）")
        void switch_mapArray_contains_function() {
            Map<String, Object> req = new HashMap<>();
            req.put("types", new String[]{"A", "B"});
            ProceedingJoinPoint pjp = mockPjp(new Object[]{req}, new String[]{"req"});

            String resultTrue = evaluator.evaluate(
                "#switch(#contains(#req.types,'A'),'true','mock/a.json','mock/d.json')", pjp);
            String resultFalse = evaluator.evaluate(
                "#switch(#contains(#req.types,'C'),'true','mock/a.json','mock/d.json')", pjp);

            assertThat(resultTrue).isEqualTo("mock/a.json");
            assertThat(resultFalse).isEqualTo("mock/d.json");
        }

        @Test
        @DisplayName("String 参数不支持 .field 取值")
        void stringParamWithField_throws() {
            String body = "{\"userType\":\"A\",\"name\":\"test\"}";
            ProceedingJoinPoint pjp = mockPjp(new Object[]{body}, new String[]{"body"});
            assertThatThrownBy(() ->
                evaluator.evaluate("#switch(#body.userType,'A','mock/a.json','mock/d.json')", pjp))
                .isInstanceOf(MockDataException.class);
        }

        @Test
        @DisplayName("不允许任意方法调用（只允许 contains）")
        void methodInvocation_notAllowed() {
            ProceedingJoinPoint pjp = mockPjp(new Object[]{"a"}, new String[]{"type"});
            assertThatThrownBy(() -> evaluator.evaluate("#{#type.toUpperCase()}", pjp))
                .isInstanceOf(MockDataException.class);
        }

        @Test
        @DisplayName("不允许类型引用 T(...)")
        void typeReference_notAllowed() {
            ProceedingJoinPoint pjp = mockPjp(new Object[]{"a"}, new String[]{"type"});
            assertThatThrownBy(() -> evaluator.evaluate("#{T(java.lang.Runtime)}", pjp))
                .isInstanceOf(MockDataException.class);
        }

        @Test
        @DisplayName("不允许 Bean 引用 @xxx")
        void beanReference_notAllowed() {
            ProceedingJoinPoint pjp = mockPjp(new Object[]{"a"}, new String[]{"type"});
            assertThatThrownBy(() -> evaluator.evaluate("#{@environment}", pjp))
                .isInstanceOf(MockDataException.class);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ProceedingJoinPoint mockPjp(Object[] args, String[] paramNames) {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.getSignature()).thenReturn(sig);
        // paramNames 为 null 数组元素时模拟编译器未保留参数名
        when(sig.getParameterNames()).thenReturn(
            paramNames[0] == null ? null : paramNames
        );
        return pjp;
    }

    /** 测试用 Java 对象 */
    static class TestRequest {
        private final String userType;
        TestRequest(String userType) { this.userType = userType; }
        public String getUserType() { return userType; }
    }
}
