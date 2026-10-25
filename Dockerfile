FROM eclipse-temurin:25-jdk-alpine AS build
RUN apk add --no-cache nodejs npm
WORKDIR /build
COPY . .
RUN ./gradlew --no-daemon refreshCatalog
RUN ./gradlew --no-daemon installDist -x test

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /build/build/install/lolorito/ ./
EXPOSE 8080
ENTRYPOINT ["/app/bin/lolorito"]
