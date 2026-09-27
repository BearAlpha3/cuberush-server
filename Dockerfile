FROM maven:3.9.9-eclipse-temurin-8

WORKDIR /app

COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src

RUN mvn -q package

CMD ["sh", "-c", "java -jar target/cuberush-server.jar"]
