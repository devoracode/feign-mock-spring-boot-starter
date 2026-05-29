package io.github.feignmock.el;

import io.github.feignmock.exception.MockDataException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.AccessException;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectiveMethodResolver;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeLocator;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock EL（表达式语言）求值器。
 *
 * <p>当 {@code @MockMethod(jsonFile=...)} 的值以 {@code #} 开头时，
 * 视为 EL 表达式并由本类解析，否则直接作为静态文件路径使用。
 *
 * <h3>内置函数</h3>
 * <pre>
 * #switch(#param, 'A', 'mock/a.json', 'B', 'mock/b.json', 'mock/default.json')
 * </pre>
 * <ul>
 *   <li>第一个参数：从方法入参中取值的表达式（见下方参数引用规则）</li>
 *   <li>后续参数：成对的 {@code 匹配值, 文件路径}，最后一个单独参数为默认路径（可省略）</li>
 * </ul>
 *
 * <h3>参数引用规则</h3>
 * <ul>
 *   <li>{@code #paramName}：按参数名引用（需编译时保留参数名，或使用 {@code -parameters}）</li>
 *   <li>{@code #p0}、{@code #p1}：按参数索引引用（0-based，始终可用）</li>
 *   <li>{@code #paramName.field}：从 Java 对象或 Map 中取嵌套字段</li>
 * </ul>
 *
 * <h3>参数类型支持</h3>
 * <ul>
 *   <li>基础类型及其包装类、String：直接转字符串比较</li>
 *   <li>Map：按 key 取值</li>
 *   <li>Java 对象：通过 getter 取值</li>
 * </ul>
 *
 * @author Wenjie
 * @since 1.1.0
 */
public class MockElEvaluator {

    private static final Logger log = LoggerFactory.getLogger(MockElEvaluator.class);

    /** EL 表达式前缀标识 */
    public static final String EL_PREFIX = "#";

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public MockElEvaluator() {}

    /**
     * 判断给定字符串是否为 EL 表达式。
     *
     * @param expression 待判断的字符串
     * @return {@code true} 表示是 EL 表达式
     */
    public static boolean isExpression(String expression) {
        return StringUtils.hasText(expression) && expression.trim().startsWith(EL_PREFIX);
    }

    /**
     * 对 EL 表达式求值，返回解析后的文件路径。
     *
     * @param expression EL 表达式，如 {@code #switch(#type,'A','mock/a.json','mock/d.json')}
     * @param pjp        AOP 切入点，用于获取方法参数
     * @return 解析后的文件路径
     * @throws MockDataException 表达式语法错误或求值失败时抛出
     */
    public String evaluate(String expression, ProceedingJoinPoint pjp) {
        String expr = normalize(expression);
        log.debug("[MockEL] Evaluating expression: {}", expr);

        StandardEvaluationContext context = buildContext(pjp);
        try {
            Object value = getExpression(expr).getValue(context);
            if (value == null) {
                return null;
            }
            if (value instanceof String) {
                return (String) value;
            }
            return String.valueOf(value);
        } catch (Exception e) {
            MockDataException nested = findMockDataException(e);
            if (nested != null) {
                throw nested;
            }
            throw new MockDataException("Failed to evaluate EL expression: [" + expression + "]. Cause: " + e.getMessage(), e);
        }
    }

    private MockDataException findMockDataException(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof MockDataException) {
                return (MockDataException) cur;
            }
            cur = cur.getCause();
        }
        return null;
    }

    private Expression getExpression(String expr) {
        return expressionCache.computeIfAbsent(expr, parser::parseExpression);
    }

    private String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String expr = raw.trim();
        if (expr.startsWith("#{") && expr.endsWith("}")) {
            return expr.substring(2, expr.length() - 1);
        }
        return expr;
    }

    private StandardEvaluationContext buildContext(ProceedingJoinPoint pjp) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setTypeLocator(new DenyAllTypeLocator());
        context.setConstructorResolvers(Collections.emptyList());
        context.setPropertyAccessors(Arrays.asList(new MapAccessor(), new SafeReflectivePropertyAccessor()));
        context.setMethodResolvers(Collections.singletonList(new SafeMethodResolver()));

        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Object[] args = pjp.getArgs();

        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
        }

        String[] paramNames = sig.getParameterNames();
        if (paramNames == null || paramNames.length == 0) {
            paramNames = resolveParamNamesFromInterface(sig.getMethod());
        }
        if (paramNames != null) {
            for (int i = 0; i < Math.min(paramNames.length, args.length); i++) {
                if (StringUtils.hasText(paramNames[i])) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }
        }

        try {
            Method switchMethod = MockSpelFunctions.class.getMethod("switchOf", Object.class, Object[].class);
            context.registerFunction("switch", switchMethod);
            Method containsMethod = MockSpelFunctions.class.getMethod("containsOf", Object.class, Object.class);
            context.registerFunction("contains", containsMethod);
        } catch (NoSuchMethodException e) {
            throw new MockDataException("Failed to register SpEL function", e);
        }

        return context;
    }

    private String[] resolveParamNamesFromInterface(java.lang.reflect.Method implMethod) {
        Class<?> declaringClass = implMethod.getDeclaringClass();
        for (Class<?> iface : declaringClass.getInterfaces()) {
            try {
                java.lang.reflect.Method ifaceMethod = iface.getMethod(
                    implMethod.getName(), implMethod.getParameterTypes());
                java.lang.reflect.Parameter[] parameters = ifaceMethod.getParameters();
                if (parameters.length > 0 && parameters[0].isNamePresent()) {
                    String[] names = new String[parameters.length];
                    for (int i = 0; i < parameters.length; i++) {
                        names[i] = parameters[i].getName();
                    }
                    return names;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    static final class MockSpelFunctions {
        public static String switchOf(Object value, Object... args) {
            String key = value == null ? "" : String.valueOf(value);
            int i = 0;
            while (i + 1 < args.length) {
                String caseVal = args[i] == null ? "" : String.valueOf(args[i]);
                String filePath = args[i + 1] == null ? null : String.valueOf(args[i + 1]);
                if (caseVal.equals(key)) {
                    return filePath;
                }
                i += 2;
            }
            if (i < args.length) {
                Object def = args[i];
                return def == null ? null : String.valueOf(def);
            }
            throw new MockDataException("No matching case in #switch for value: [" + key + "], and no default provided.");
        }

        public static boolean containsOf(Object target, Object needle) {
            if (target == null) {
                return false;
            }
            String n = needle == null ? null : String.valueOf(needle);
            if (target instanceof String) {
                return n != null && ((String) target).contains(n);
            }
            if (target instanceof Collection) {
                Collection<?> c = (Collection<?>) target;
                if (c.contains(needle)) {
                    return true;
                }
                if (n == null) {
                    return false;
                }
                for (Object v : c) {
                    if (n.equals(String.valueOf(v))) {
                        return true;
                    }
                }
                return false;
            }
            Class<?> type = target.getClass();
            if (type.isArray()) {
                int len = Array.getLength(target);
                for (int i = 0; i < len; i++) {
                    Object v = Array.get(target, i);
                    if (needle == null) {
                        if (v == null) {
                            return true;
                        }
                    } else if (needle.equals(v) || String.valueOf(needle).equals(String.valueOf(v))) {
                        return true;
                    }
                }
                return false;
            }
            return false;
        }
    }

    static final class DenyAllTypeLocator extends StandardTypeLocator {
        @Override
        public Class<?> findType(String typeName) {
            throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
        }
    }

    static final class SafeReflectivePropertyAccessor extends ReflectivePropertyAccessor {
        private static final Set<String> DENY;

        static {
            java.util.Set<String> deny = new java.util.HashSet<>();
            deny.add("class");
            deny.add("classLoader");
            deny.add("protectionDomain");
            DENY = java.util.Collections.unmodifiableSet(deny);
        }

        @Override
        public boolean canRead(org.springframework.expression.EvaluationContext context, Object target, String name) throws AccessException {
            if (DENY.contains(name)) {
                return false;
            }
            return super.canRead(context, target, name);
        }
    }

    static final class SafeMethodResolver implements MethodResolver {
        private static final Set<String> ALLOW;
        private final ReflectiveMethodResolver delegate = new ReflectiveMethodResolver();

        static {
            java.util.Set<String> allow = new java.util.HashSet<>();
            allow.add("contains");
            ALLOW = java.util.Collections.unmodifiableSet(allow);
        }

        @Override
        public MethodExecutor resolve(org.springframework.expression.EvaluationContext context,
                                      Object targetObject,
                                      String name,
                                      java.util.List<TypeDescriptor> argumentTypes) throws AccessException {
            if (targetObject == null || !ALLOW.contains(name)) {
                return null;
            }
            Class<?> targetType = targetObject.getClass();
            if (String.class.isAssignableFrom(targetType)) {
                return delegate.resolve(context, targetObject, name, argumentTypes);
            }
            if (java.util.Collection.class.isAssignableFrom(targetType)) {
                return delegate.resolve(context, targetObject, name, argumentTypes);
            }
            return null;
        }
    }
}
