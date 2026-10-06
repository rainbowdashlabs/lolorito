FROM eclipse-temurin:25-alpine AS build

# node/npm is only needed for the Vite SPA bundle. The catalog refresh
# moved to the JVM (de.chojo.lolorito.catalog.CatalogRefreshCli) so the
# refresh path no longer depends on node.
RUN apk add --no-cache nodejs npm

WORKDIR /src
COPY . .

# Best-effort refresh of the bundled item / recipe / desynth JSONs
# from XIVAPI + Teamcraft. On upstream failure the CLI leaves the
# committed seed in place — the image build never blocks on an outage.
RUN ./gradlew --no-daemon refreshCatalog

RUN ./gradlew --no-daemon installDist -x test

FROM eclipse-temurin:25-alpine AS runtime

WORKDIR /app

COPY --from=build /src/build/install/lolorito ./

EXPOSE 8080

ENTRYPOINT ["/app/bin/lolorito"]
