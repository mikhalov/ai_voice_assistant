FROM maven:3-amazoncorretto-20-debian AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package

FROM openjdk:20-jdk-slim-buster

RUN apt-get -y update && apt-get -y upgrade && apt-get install -y --no-install-recommends ffmpeg

COPY --from=build /app/target/ai_interviewer-0.0.1-SNAPSHOT.jar ai_assistant.jar

EXPOSE 8080

ENTRYPOINT ["java", "--enable-preview", "-jar", "ai_assistant.jar"]