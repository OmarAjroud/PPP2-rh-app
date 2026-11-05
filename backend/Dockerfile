# syntax=docker/dockerfile:1
# Image du back-end en deux étapes : la compilation se fait dans Docker (ni Java ni Maven requis sur la machine).

# ---------- 1. Compilation ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Dépendances d'abord, dans leur propre couche : retéléchargées seulement si pom.xml change
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Les tests tournent dans la CI (./mvnw verify, avec Docker) : ici on construit seulement l'application
RUN ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/rh-*.jar extract --layers --launcher --destination /build/extracted

# ---------- 2. Exécution ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Utilisateur sans privilèges : l'application ne tourne jamais en root
RUN groupadd --system rh && useradd --system --gid rh --home-dir /app rh

# Couches de la moins à la plus souvent modifiée : une modification du code ne reconstruit que la dernière
COPY --from=build --chown=rh:rh /build/extracted/dependencies/ ./
COPY --from=build --chown=rh:rh /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=rh:rh /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=rh:rh /build/extracted/application/ ./

USER rh
EXPOSE 8080

# TZ : fuseau de l'entreprise (« aujourd'hui » pour les règles de congés, heures des notifications et de l'audit)
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Africa/Tunis \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# Docker sait si l'application est prête (et la redémarre si elle ne répond plus)
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"UP"' || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
