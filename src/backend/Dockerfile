# Multi-stage build (xây nhiều tầng): tầng 1 biên dịch ra .jar, tầng 2 chỉ chạy.
# -> image cuối nhẹ, không chứa Maven/source.

# --- Stage 1: build ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy Maven wrapper + pom trước để tận dụng cache layer (chỉ tải lại deps khi pom đổi).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline

# Copy source và đóng gói (bỏ test — test cần Postgres/Docker, chạy ở CI).
COPY src/ src/
RUN ./mvnw -q -B -DskipTests clean package

# --- Stage 2: runtime ---
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Chạy bằng user không phải root cho an toàn.
RUN useradd --system --no-create-home spring
USER spring

COPY --from=build /app/target/stockpile-backend-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
