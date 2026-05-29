# feign-mock-spring-boot-starter

为 OpenFeign 客户端提供**注解驱动**的 Mock 数据支持。
在内网不可达的开发/测试环境中，通过 `@MockMethod` 注解将 Feign 请求替换为本地 JSON 或自定义逻辑，
生产环境关闭开关后**零开销、零侵入**。

---

## 特性

| 特性 | 说明 |
|------|------|
| 三种数据源 | 自定义 Provider > 指定 JSON 文件 > key 自动查找（配置文件/本地文件） |
| 零生产侵入 | `feign.mock.enabled=false`（默认）时切面不工作，无任何额外开销 |
| 可扩展数据源 | 实现 `MockDataSource` 接口并注册为 Bean，自动接入加载链路 |
| 方法级粒度 | 同一 Feign 接口可部分方法 Mock、部分方法真实调用 |

---

## 快速接入

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.github.devoracode</groupId>
    <artifactId>feign-mock-spring-boot-starter</artifactId>
    <version>1.2.1</version>
</dependency>
```

### 2. 开启开关

```yaml
# application-dev.yml（仅开发环境）
feign:
  mock:
    enabled: true
```

> 生产环境不配置此项（默认 false）。

### 3. 标注 `@MockMethod`

```java
@FeignClient(name = "user-service")
public interface UserFeignClient {

    // ① key 自动推导（接口类名首字母小写.方法名）→ 查配置文件或 classpath:mock/userFeignClient/getUserById.json
    @MockMethod
    @GetMapping("/api/users/{userId}")
    UserDTO getUserById(@PathVariable Long userId);

    // ② 手动指定 key
    @MockMethod(value = "userFeignClient.listUsers")
    @GetMapping("/api/users")
    List<UserDTO> listUsers(@RequestParam Integer page, @RequestParam Integer size);

    // ③ 直接指定 JSON 文件（路径相对 classpath 根目录）
    @MockMethod(jsonFile = "mock/order/getOrder.json")
    @GetMapping("/api/orders/{orderId}")
    OrderDTO getOrder(@PathVariable Long orderId);

    // ④ 自定义 Provider（可访问入参、注入 Bean、抛业务异常）
    @MockMethod(provider = UserMockProvider.class)
    @GetMapping("/api/users/{userId}")
    UserDTO getUserWithRole(@PathVariable Long userId);
}
```

---

## 数据源详解

### 优先级顺序

```
Provider（provider=）
    ↓ 未配置
JSON 文件（jsonFile=）
    ↓ 未配置
key 查找
    ├─ 配置文件（feign.mock.responses.{key}）  ← order=10，先查
    └─ 本地文件（classpath:mock/{key}.json）   ← order=20，后查
```

### ① 自定义 Provider

实现 `MockDataProvider` 接口并注册为 Spring Bean（支持 `@Autowired`）：

```java
@Component
public class UserMockProvider implements MockDataProvider {

    @Override
    public Object provide(ProceedingJoinPoint pjp, Method method) {
        Long userId = (Long) pjp.getArgs()[0];

        // 根据入参动态返回不同数据
        if (userId == 1L) {
            return UserDTO.builder().userId(1L).username("admin").role("ADMIN").build();
        }
        // 模拟业务异常
        if (userId < 0) {
            throw new BizException("用户 ID 不合法");
        }
        return UserDTO.builder().userId(userId).username("user_" + userId).build();
    }
}
```

### ② 指定 JSON 文件

```java
@MockMethod(jsonFile = "mock/user/special-case.json")
UserDTO getSpecialUser(Long userId);
```

文件放置在 `src/main/resources/mock/user/special-case.json`。

### ③ key 查找

**配置文件方式：**

```yaml
feign:
  mock:
    enabled: true
    responses:
      userFeignClient.getUserById: '{"userId":1,"username":"nacos_user","status":"ACTIVE"}'
      userFeignClient.listUsers: '[{"userId":1},{"userId":2}]'
```

**本地文件方式：**

```
src/main/resources/
└── mock/
    └── userFeignClient/
        ├── getUserById.json
        └── listUsers.json
```

key 转换规则：`userFeignClient.getUserById` → `classpath:mock/userFeignClient/getUserById.json`

---

## 高级用法

### jsonFile EL 表达式（#switch 动态选择文件）

当 `@MockMethod(jsonFile = ...)` 的值以 `#` 开头时，会被识别为 Spring SpEL 并在运行时求值，得到最终要读取的 JSON 文件路径（同时支持 `#...` 与 `#{...}` 两种写法）。

内置函数 `#switch(...)`，用于根据方法入参动态选择不同的 mock 文件：

```java
// 根据入参 type 决定读取哪个 JSON 文件
@MockMethod(jsonFile = "#switch(#type,'A','mock/el/type_a.json','B','mock/el/type_b.json','mock/el/default.json')")
UserDTO getByType(String type);
```

#### #switch 语法

```
#switch(<参数引用>, '<case1>', '<file1>', '<case2>', '<file2>', ..., '<defaultFile>')
```

- 第 1 个参数：参数引用表达式（见下方“参数引用规则”）
- 后续参数：成对出现的 `case 值` 与 `文件路径`
- 最后可选：默认文件路径（不提供默认值且无 case 命中时会抛异常）

#### 参数引用规则

- `#paramName`：按参数名引用（需要编译保留参数名，或使用 `-parameters`）
- `#p0` / `#p1`：按参数下标引用（0-based，始终可用）
- 支持嵌套取值：`#paramName.field`、`#p0.userType`、`#req.user.type`
- 支持包含判断：`#req.types.contains('A')`（集合/String），`#contains(#req.types,'A')`（数组/集合/String；返回 `true/false`，通常配合 `case='true'` 使用）

取值对象类型支持：

- Map：按 key 取值，如 `#req.userType`
- Java 对象：优先调用 getter（`getXxx()/isXxx()`），否则反射字段
- String：仅作为普通字符串使用（不支持在 String 上通过 `.field` 取嵌套字段）

#### 文件路径写法

表达式返回值会被当作 classpath 相对路径读取，常见写法：

- `mock/el/type_a.json`
- 也可以返回带前缀的路径：`classpath:mock/el/type_a.json`

#### 注意点

- `#switch` 的 case 比较基于字符串；例如入参为 `1L` 时，对应 case 写成 `'1'`
- 出于安全考虑，表达式能力做了限制：不支持 `T(...)`、`new`、`@bean` 等用法，仅放行 `contains` 方法调用
- 表达式求值失败（语法错误/越权调用/无 default 且未命中等）会抛出 `MockDataException`

### failFast 控制

```java
// 找不到数据时返回 null（不抛异常），适合可选接口
@MockMethod(value = "optional.data", failFast = false)
UserDTO getOptionalUser(Long userId);
```

### 扩展自定义数据源

实现 `MockDataSource` 接口，注册为 Bean 后自动加入查找链：

```java
@Component
public class RedisMockDataSource implements MockDataSource {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public Optional<String> findByKey(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get("mock:" + key));
    }

    @Override
    public int getOrder() {
        return 5;  // 数字越小优先级越高，内置配置文件数据源为 10
    }
}
```

---

## 配置项参考

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `feign.mock.enabled` | Boolean | `false` | 全局开关，`true` 才激活所有切面 |
| `feign.mock.responses.*` | Map | 空 | key-value 形式的 Mock 数据 |

---

## 兼容性

| 组件 | 版本 |
|------|------|
| Spring Boot | 2.x / 3.x |
| JDK | 1.8+ |
| Spring Cloud OpenFeign | 3.x（Spring Boot 2.x）/ 4.x（Spring Boot 3.x） |
