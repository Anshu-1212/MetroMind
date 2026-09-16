# MetroMind — Backend

## Purpose

This directory contains the Spring Boot backend for the MetroMind metro route planner.

## Technologies

- **Java** — programming language
- **Spring Boot** — application framework
- **Maven** — build and dependency management

## How to Run

```bash
mvn spring-boot:run
```

The server starts at `http://localhost:8080`.

## How to Build

```bash
mvn clean package
```

Produces an executable JAR in `target/`.

## Health Endpoint

```
GET /api/health
```

Returns:

```json
{
  "status": "ok",
  "service": "MetroMind Backend"
}
```

## Run Tests

```bash
mvn test
```
