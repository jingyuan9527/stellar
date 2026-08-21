# Commands

## Frontend (`frontend/`)

```
pnpm dev          # :5173
pnpm typecheck    # vue-tsc -b --noEmit
pnpm build        # vue-tsc -b && vite build
```

## Backend (`backend/`)

```
mvn -q compile -DskipTests
mvn test -Dtest='SafeUrlValidatorTest,SubjectUtilsTest' -DfailIfNoTests=false
mvn test
mvn test jacoco:report   # target/site/jacoco/index.html
$env:SPRING_PROFILES_ACTIVE="local"; mvn spring-boot:run  # :8080
```

覆盖率（2026-08-02）：行 69.5% / 分支 58.3%，68 类 513 例，Controller 全补测。`AiChatService` 17.2%（异步 SSE 非确定性，刻意）。

单测约定：
- 内联 `final HttpClient` → `ReflectUtil.setFinalField` 注入 mock；`send` 用 `doReturn(...).when(mock).send(any(),any())`
- `InetAddress.getAllByName` 用 `MockedStatic<InetAddress>`；登录态 `MockedStatic<StpUtil>`
- MP `insert/updateById` 重载用 `ArgumentCaptor`，Controller 纯 Mockito `new Controller(mockService)`

提交前：`pnpm typecheck` + `mvn -q compile -DskipTests`
