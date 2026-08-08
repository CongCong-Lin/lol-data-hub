# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 先复制 pom 文件，利用 Docker 层缓存加速依赖下载
COPY pom.xml .
COPY backend/lol-datahub-domain/pom.xml backend/lol-datahub-domain/
COPY backend/lol-datahub-source/pom.xml backend/lol-datahub-source/
COPY backend/lol-datahub-infrastructure/pom.xml backend/lol-datahub-infrastructure/
COPY backend/lol-datahub-collector/pom.xml backend/lol-datahub-collector/
COPY backend/lol-datahub-statistics/pom.xml backend/lol-datahub-statistics/
COPY backend/lol-datahub-application/pom.xml backend/lol-datahub-application/

RUN mvn dependency:go-offline -B

# 复制全部源码并打包
COPY backend/ backend/
RUN mvn package -B -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /workspace/backend/lol-datahub-application/target/lol-datahub-application-*.jar app.jar

RUN chown -R app:app /app
USER app

# 容器化 JVM 参数：使用 UseContainerSupport 自动识别容器内存限制
ENV JAVA_OPTS="-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:InitialRAMPercentage=50.0 \
-Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=30s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
