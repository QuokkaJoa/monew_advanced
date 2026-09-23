# 1. 빌드 스테이지
FROM amazoncorretto:17 AS build
WORKDIR /app

ARG PROJECT_NAME=monew
ARG PROJECT_VERSION=1.2-M8
# (빌드시 활용할 ARG/ENV만 남기거나 생략)

COPY gradlew .
COPY gradle/ gradle/
COPY settings.gradle build.gradle ./
RUN dnf install -y findutils && chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src/ src/
RUN ./gradlew bootJar --no-daemon

# 2. 런타임 스테이지
FROM amazoncorretto:17
WORKDIR /app
VOLUME /tmp

COPY --from=build /app/build/libs/*.jar app.jar

# 앱이 80 포트로 동작하도록 설정했다면
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
