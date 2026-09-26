# syntax=docker/dockerfile:1
# 压测用的应用镜像，由 compose.loadtest.yaml 构建；本地开发仍在 IDEA 中运行
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
# 缓存挂载复用 Maven 依赖与 wrapper 下载的 Maven
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B -pl code-nest-app -am package -DskipTests
# 按依赖、加载器、应用代码分层解开，改代码后只有最后一层变化
RUN java -Djarmode=tools -jar code-nest-app/target/code-nest-app-*.jar extract --layers --launcher --destination /layers

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
EXPOSE 8080 8081
ENTRYPOINT ["java", "-Xmx1g", "org.springframework.boot.loader.launch.JarLauncher"]
