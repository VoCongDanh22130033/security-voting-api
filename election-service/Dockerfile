# Sử dụng một base image Java 17 chuẩn từ Eclipse Temurin
FROM eclipse-temurin:17-jdk-focal

# Đặt thư mục làm việc bên trong container
WORKDIR /app

# Sao chép file .jar đã được build từ thư mục target của Maven vào container
COPY target/election-service-0.0.1-SNAPSHOT.jar app.jar

# Expose port mà ứng dụng sẽ chạy
EXPOSE 8083

# Lệnh để chạy ứng dụng khi container khởi động
ENTRYPOINT ["java","-jar","app.jar"]
