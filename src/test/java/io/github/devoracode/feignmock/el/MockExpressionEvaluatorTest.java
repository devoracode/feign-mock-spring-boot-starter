package io.github.devoracode.feignmock.el;

import io.github.devoracode.feignmock.exception.MockDataException;
import io.github.devoracode.feignmock.spel.MockExpressionEvaluator;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MockExpressionEvaluator")
class MockExpressionEvaluatorTest {

	private MockExpressionEvaluator evaluator;

	@BeforeEach
	void setUp() {
		this.evaluator = new MockExpressionEvaluator();
	}

	@Test
	@DisplayName("isExpression returns true for expressions")
	void isExpressionTrue() {
		assertThat(MockExpressionEvaluator.isExpression("#switch(#type,'A','a.json')")).isTrue();
	}

	@Test
	@DisplayName("isExpression returns false for static resource paths")
	void isExpressionFalse() {
		assertThat(MockExpressionEvaluator.isExpression("mock/user.json")).isFalse();
		assertThat(MockExpressionEvaluator.isExpression("")).isFalse();
		assertThat(MockExpressionEvaluator.isExpression(null)).isFalse();
	}

	@Nested
	@DisplayName("#switch")
	class SwitchTests {

		@Test
		@DisplayName("matches string parameter case A")
		void switchStringParamMatchA() {
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { "A" }, new String[] { "type" });
			String result = evaluator.evaluate(
					"#switch(#type,'A','mock/a.json','B','mock/b.json','mock/d.json')", joinPoint);
			assertThat(result).isEqualTo("mock/a.json");
		}

		@Test
		@DisplayName("matches string parameter case B")
		void switchStringParamMatchB() {
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { "B" }, new String[] { "type" });
			String result = evaluator.evaluate(
					"#switch(#type,'A','mock/a.json','B','mock/b.json','mock/d.json')", joinPoint);
			assertThat(result).isEqualTo("mock/b.json");
		}

		@Test
		@DisplayName("falls back to default value")
		void switchStringParamDefault() {
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { "C" }, new String[] { "type" });
			String result = evaluator.evaluate(
					"#switch(#type,'A','mock/a.json','B','mock/b.json','mock/d.json')", joinPoint);
			assertThat(result).isEqualTo("mock/d.json");
		}

		@Test
		@DisplayName("supports indexed parameter references")
		void switchIndexParam() {
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { "A" }, new String[] { "type" });
			String result = evaluator.evaluate("#switch(#p0,'A','mock/a.json','mock/d.json')", joinPoint);
			assertThat(result).isEqualTo("mock/a.json");
		}

		@Test
		@DisplayName("throws when no case matches and no default is provided")
		void switchNoDefaultNoMatchThrows() {
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { "C" }, new String[] { "type" });
			assertThatThrownBy(() -> evaluator.evaluate("#switch(#type,'A','mock/a.json','B','mock/b.json')",
					joinPoint))
					.isInstanceOf(MockDataException.class)
					.hasMessageContaining("No matching case");
		}

		@Test
		@DisplayName("supports map parameter property access")
		void switchMapParam() {
			Map<String, Object> request = new HashMap<>();
			request.put("userType", "A");
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { request }, new String[] { "req" });
			String result = evaluator.evaluate("#switch(#req.userType,'A','mock/a.json','mock/d.json')", joinPoint);
			assertThat(result).isEqualTo("mock/a.json");
		}

		@Test
		@DisplayName("supports Java object parameter property access")
		void switchJavaObjectParam() {
			TestRequest request = new TestRequest("B");
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { request }, new String[] { "req" });
			String result = evaluator.evaluate(
					"#switch(#req.userType,'A','mock/a.json','B','mock/b.json','mock/d.json')", joinPoint);
			assertThat(result).isEqualTo("mock/b.json");
		}

		@Test
		@DisplayName("supports collection contains checks")
		void switchMapCollectionContains() {
			Map<String, Object> request = new HashMap<>();
			request.put("types", Arrays.asList("A", "B"));
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { request }, new String[] { "req" });

			String resultTrue = evaluator.evaluate(
					"#switch(#req.types.contains('A'),'true','mock/a.json','mock/d.json')", joinPoint);
			String resultFalse = evaluator.evaluate(
					"#switch(#req.types.contains('C'),'true','mock/a.json','mock/d.json')", joinPoint);

			assertThat(resultTrue).isEqualTo("mock/a.json");
			assertThat(resultFalse).isEqualTo("mock/d.json");
		}

		@Test
		@DisplayName("supports array contains checks via #contains")
		void switchMapArrayContainsFunction() {
			Map<String, Object> request = new HashMap<>();
			request.put("types", new String[] { "A", "B" });
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { request }, new String[] { "req" });

			String resultTrue = evaluator.evaluate(
					"#switch(#contains(#req.types,'A'),'true','mock/a.json','mock/d.json')", joinPoint);
			String resultFalse = evaluator.evaluate(
					"#switch(#contains(#req.types,'C'),'true','mock/a.json','mock/d.json')", joinPoint);

			assertThat(resultTrue).isEqualTo("mock/a.json");
			assertThat(resultFalse).isEqualTo("mock/d.json");
		}

		@Test
		@DisplayName("evaluates #choose conditions in order")
		void chooseMultiConditions() {
			Map<String, Object> request = new HashMap<>();
			request.put("types", Arrays.asList("B"));
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { request }, new String[] { "req" });

			String result = evaluator.evaluate(
					"#choose(#contains(#req.types,'A'),'mock/a.json', #contains(#req.types,'B'),'mock/b.json', 'mock/d.json')",
					joinPoint);
			assertThat(result).isEqualTo("mock/b.json");
		}

		@Test
		@DisplayName("falls back to #choose default value")
		void chooseDefault() {
			Map<String, Object> request = new HashMap<>();
			request.put("types", Arrays.asList("C"));
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { request }, new String[] { "req" });

			String result = evaluator.evaluate(
					"#choose(#contains(#req.types,'A'),'mock/a.json', #contains(#req.types,'B'),'mock/b.json', 'mock/d.json')",
					joinPoint);
			assertThat(result).isEqualTo("mock/d.json");
		}

		@Test
		@DisplayName("rejects property access on plain strings")
		void stringParamWithFieldThrows() {
			String body = "{\"userType\":\"A\",\"name\":\"test\"}";
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { body }, new String[] { "body" });
			assertThatThrownBy(() -> evaluator.evaluate("#switch(#body.userType,'A','mock/a.json','mock/d.json')",
					joinPoint))
					.isInstanceOf(MockDataException.class);
		}

		@Test
		@DisplayName("rejects arbitrary method invocations")
		void methodInvocationNotAllowed() {
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { "a" }, new String[] { "type" });
			assertThatThrownBy(() -> evaluator.evaluate("#{#type.toUpperCase()}", joinPoint))
					.isInstanceOf(MockDataException.class);
		}

		@Test
		@DisplayName("rejects type references")
		void typeReferenceNotAllowed() {
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { "a" }, new String[] { "type" });
			assertThatThrownBy(() -> evaluator.evaluate("#{T(java.lang.Runtime)}", joinPoint))
					.isInstanceOf(MockDataException.class);
		}

		@Test
		@DisplayName("rejects bean references")
		void beanReferenceNotAllowed() {
			ProceedingJoinPoint joinPoint = mockJoinPoint(new Object[] { "a" }, new String[] { "type" });
			assertThatThrownBy(() -> evaluator.evaluate("#{@environment}", joinPoint))
					.isInstanceOf(MockDataException.class);
		}

	}

	private ProceedingJoinPoint mockJoinPoint(Object[] args, String[] parameterNames) {
		ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
		MethodSignature signature = mock(MethodSignature.class);
		when(joinPoint.getArgs()).thenReturn(args);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(signature.getParameterNames()).thenReturn(parameterNames[0] == null ? null : parameterNames);
		return joinPoint;
	}

	static class TestRequest {

		private final String userType;

		TestRequest(String userType) {
			this.userType = userType;
		}

		public String getUserType() {
			return this.userType;
		}

	}

}
