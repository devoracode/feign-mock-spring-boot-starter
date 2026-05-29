# feign-mock-spring-boot-starter

为 OpenFeign 客户端提供注解驱动的 Mock 响应能力：在开发/测试环境中可把 Feign 调用替换为本地 JSON 或自定义逻辑；关闭开关后不生效，生产环境保持“零侵入”。

## 功能概览

- 方法级 Mock：同一个 Feign 接口里可以只 Mock 部分方法，其他方法仍走真实调用
- 三种数据来源（按优先级）：
  - Provider：代码生成/动态返回（最高优先级）
  - jsonFile：读取 classpath JSON 文件（支持受限 SpEL 动态计算路径）
  - key：按 key 在配置映射与本地文件中查找（自动推导 key）
- 可扩展：自定义 `MockDataSource` 即可接入数据源链
- 失败策略：找不到数据可选择抛错或返回 null（`failFast`）

## 快速开始

### 1）引入依赖

```xml
<dependency>
  <groupId>io.github.devoracode</groupId>
  <artifactId>feign-mock-spring-boot-starter</artifactId>
  <version>1.3.0</version>
</dependency>
```

### 2）开启开关（仅建议开发/测试环境）

```yaml
feign:
  mock:
    enabled: true
```

### 3）在 Feign 方法上标注 `@MockMethod`

```java
@FeignClient(name = "user-service")
public interface UserFeignClient {

  @MockMethod
  @GetMapping("/api/users/{userId}")
  UserDTO getUserById(@PathVariable Long userId);

  @MockMethod(value = "userFeignClient.listUsers")
  @GetMapping("/api/users")
  List<UserDTO> listUsers(@RequestParam Integer page, @RequestParam Integer size);

  @MockMethod(jsonFile = "mock/userClient/getUserById.json")
  @GetMapping("/api/users/{userId}")
  UserDTO getUserByIdFromFile(@PathVariable Long userId);

  @MockMethod(provider = UserMockProvider.class)
  @GetMapping("/api/users/{userId}")
  UserDTO getUserByProvider(@PathVariable Long userId);
}
```

## 工作原理（简述）

- 当 `feign.mock.enabled=true` 时，starter 自动装配 `MockMethodAspect`，拦截所有标注了 `@MockMethod` 的方法
- 每次拦截按以下优先级选择数据来源：
  1. `provider`（自定义 Provider）
  2. `jsonFile`（读取文件；若是表达式则先计算路径）
  3. `value`（key 查找：先配置映射，再本地文件）
- 反序列化使用 Jackson，支持泛型返回值（例如 `List<UserDTO>`）

## `@MockMethod` 参数说明

- `value`：Mock key。为空时自动推导为 `interfaceSimpleName.methodName`（接口类名首字母小写）
  - `UserFeignClient#getUserById` → `userFeignClient.getUserById`
- `jsonFile`：指定 JSON 文件路径（相对 classpath 根目录），例如 `mock/userClient/getUserById.json`
  - 如果以 `#` 开头或使用 `#{...}`，按 SpEL 表达式求值
- `provider`：自定义数据提供者（最高优先级）
- `failFast`：找不到/解析失败时是否抛异常；为 `false` 时返回 `null`
- `description`：日志标签与可读描述

## 数据来源详解

### 1）Provider（最高优先级）

实现 `MockDataProvider` 并注册为 Spring Bean（支持 `@Autowired`），然后在注解上指定 `provider`：

```java
@Component
public class UserMockProvider implements MockDataProvider {
  @Override
  public Object provide(ProceedingJoinPoint pjp, Method method) {
    Long userId = (Long) pjp.getArgs()[0];
    if (userId == 1L) {
      return UserDTO.builder().userId(1L).username("admin").build();
    }
    return UserDTO.builder().userId(userId).username("user_" + userId).build();
  }
}
```

说明：
- Provider 优先从 Spring 容器获取；若不是 Spring Bean，会尝试无参构造反射创建并缓存
- Provider 可以直接抛业务异常，模拟下游报错

### 2）jsonFile：指定 JSON 文件

```java
@MockMethod(jsonFile = "mock/userClient/getUserById.json")
UserDTO getUserById(Long userId);
```

文件推荐放置：

```
src/main/resources/
└── mock/
    └── userClient/
        └── getUserById.json
```

### 3）key 查找：配置映射优先，本地文件兜底

#### 3.1 配置映射：`feign.mock.responses`

```yaml
feign:
  mock:
    enabled: true
    responses:
      userFeignClient.getUserById: '{"userId":1,"username":"mock_user"}'
      userFeignClient.listUsers: '[{"userId":1},{"userId":2}]'
```

#### 3.2 本地文件：`classpath:mock/{key}.json`

key 会做路径转换：把 `.` 替换为 `/` 并追加 `.json`

- key：`userFeignClient.getUserById`
- 路径：`classpath:mock/userFeignClient/getUserById.json`

本地文件读取成功后会做内存缓存，避免重复 IO。

## jsonFile 的 SpEL（受限、安全模式）

当 `@MockMethod(jsonFile=...)` 以 `#` 开头或 `#{...}` 包裹时，会按 SpEL 计算出最终文件路径。

### 可用变量

- `#p0`、`#p1`：按参数下标引用（0-based，始终可用）
- `#paramName`：按参数名引用（需要编译保留参数名，或使用 `-parameters`）
- Map 支持 `#req.userType` 方式访问（按 key）

### 内置函数

#### 1）`#switch(value, case1, file1, case2, file2, ..., defaultFile)`

适合“一个离散值 → 文件映射”的场景（按字符串等值匹配）：

```java
@MockMethod(jsonFile =
  "#switch(#type,'A','mock/el/type_a.json','B','mock/el/type_b.json','mock/el/default.json')")
UserDTO getByType(String type);
```

#### 2）`#choose(cond1, file1, cond2, file2, ..., defaultFile)`

适合“多个条件按优先级命中”的场景（命中第一个为 true 的条件）：

```java
@MockMethod(jsonFile =
  "#choose(" +
    "#contains(#req.types,'A'),'mock/el/type_a.json', " +
    "#contains(#req.types,'B'),'mock/el/type_b.json', " +
    "'mock/el/default.json'" +
  ")")
UserDTO get(Map<String, Object> req);
```

#### 3）`#contains(target, needle)`

用于包含判断（返回 `true/false`），支持：

- `target` 为数组（含基本类型数组）
- `target` 为集合（List/Set）
- `target` 为 String（子串包含）

集合/String 也可以直接调用 `.contains(...)`（仅放行该方法调用）：

- `#req.types.contains('A')`（当 `types` 是集合时）

### 路径返回约定

表达式返回值会作为 classpath 路径读取，常见写法：

- `mock/el/type_a.json`
- `classpath:mock/el/type_a.json`

### 安全限制

为了降低表达式的攻击面，SpEL 能力做了限制：

- 不支持 `T(...)`（类型引用）
- 不支持 `new`（对象构造）
- 不支持 `@bean`（Bean 引用）
- 方法调用仅放行 `contains`

## 失败策略与异常

- 默认 `failFast=true`：找不到数据/反序列化失败会抛 `MockDataException`
- `failFast=false`：出现 `MockDataException` 时返回 `null`

```java
@MockMethod(value = "optional.data", failFast = false)
UserDTO getOptionalUser(Long userId);
```

## 扩展：自定义数据源 `MockDataSource`

实现 `MockDataSource` 并注册为 Spring Bean 后会自动加入数据源链，按 `getOrder()` 从小到大查找（数字越小优先级越高）。

```java
@Component
public class RedisMockDataSource implements MockDataSource {
  @Override
  public Optional<String> findByKey(String key) {
    return Optional.empty();
  }

  @Override
  public int getOrder() {
    return 5;
  }
}
```

内置数据源优先级：

- 配置映射（`feign.mock.responses`）：order=10
- 本地文件（`classpath:mock/`）：order=20

## 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `feign.mock.enabled` | Boolean | `false` | 全局开关。`true` 时自动装配并启用拦截 |
| `feign.mock.responses` | Map<String,String> | 空 | key-value Mock 数据（value 为 JSON 字符串） |

## 兼容性与说明

- JDK：项目编译目标为 1.8（运行时建议 8+）
- Spring Boot：当前以 2.6.13 依赖管理为基准进行构建与测试；starter 同时提供 `spring.factories` 与 `AutoConfiguration.imports` 声明以适配不同 Boot 版本

## 常见问题

### 1）为什么 `#paramName` 取不到参数？

需要编译保留参数名（`-parameters`）。如果无法保证，使用 `#p0/#p1` 更稳妥。

### 2）数组为什么不能直接 `.contains()`？

数组本身没有 `contains` 方法。使用内置函数：

- `#contains(#req.types,'A')`

### 3）日志里会打印什么？

拦截时会记录被拦截方法与选择的数据来源（Provider/JsonFile/Key），便于排查实际走了哪条链路。
