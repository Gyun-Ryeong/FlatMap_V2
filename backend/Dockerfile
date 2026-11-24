# syntax=docker/dockerfile:1
#
# 백엔드(Spring Boot 3.2 / Java 17) 이미지. 멀티스테이지: 빌드 도구는 최종 이미지에 들어가지 않는다.
#
#   docker build -t flatmap-backend ./backend
#
# 이미지에는 비밀값이 없다. DB 접속 정보·JWT 비밀·API 키는 실행할 때 환경변수로 주입한다
# (docker-compose.prod.yml 의 env_file). 빌드 컨텍스트에서 .env 는 backend/.dockerignore 가 막는다.

# ---- 1단계: 빌드 ----
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build

# 의존성 레이어를 먼저 받아 소스가 바뀌어도 캐시를 재사용한다.
COPY pom.xml ./
RUN mvn -B -ntp -q dependency:go-offline

COPY src ./src
# 테스트는 실제 PostGIS 가 필요해 이미지 빌드에서 돌리지 않는다(CI 의 backend 잡이 돌린다).
RUN mvn -B -ntp -q -DskipTests package \
    && mv target/navigation-*.jar /build/app.jar

# ---- 2단계: 실행 ----
FROM eclipse-temurin:17-jre-alpine

# 비루트 사용자로 실행한다.
RUN addgroup -S app && adduser -S -G app -h /app app
WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar /app/app.jar

# 운영 프로필: 개발용 기본값(localhost 주소, 짧은 비밀, 빈 관리 키)이 남으면 기동이 실패한다(ProdSettingsValidator).
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

USER app
EXPOSE 8080

# readiness: DB 연결까지 확인한다(Flyway 마이그레이션이 끝나야 앱이 뜬다). 첫 기동은 마이그레이션 때문에 시간이 걸릴 수 있다.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q '"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
