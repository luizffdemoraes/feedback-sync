# feedback-sync

Sistema de Feedback Serverless usando Azure Functions, Quarkus e Clean Architecture.

## 🚀 Validação Local com Docker Compose

Para validar a implementação localmente, consulte o guia completo: **[VALIDACAO_LOCAL.md](./VALIDACAO_LOCAL.md)**

### Início Rápido

1. **Inicie os serviços Azure (Cosmos DB, Azurite, Service Bus)**:
   ```bash
   # Windows
   .\scripts\start-local.ps1
   
   # Linux/Mac
   ./scripts/start-local.sh
   ```

2. **Execute a aplicação**:
   ```bash
   .\mvnw.cmd quarkus:dev -Dquarkus.profile=local
   ```

3. **Teste a API**:
   ```bash
   # Windows
   .\scripts\test-api.ps1
   
   # Linux/Mac
   ./scripts/test-api.sh
   ```

---

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/feedback-sync-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- REST ([guide](https://quarkus.io/guides/rest)): A Jakarta REST implementation utilizing build time processing and Vert.x. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.
- Hibernate ORM with Panache ([guide](https://quarkus.io/guides/hibernate-orm-panache)): Simplify your persistence code for Hibernate ORM via the active record or the repository pattern
- JDBC Driver - PostgreSQL ([guide](https://quarkus.io/guides/datasource)): Connect to the PostgreSQL database via JDBC

## Provided Code

### Hibernate ORM

Create your first JPA entity

[Related guide section...](https://quarkus.io/guides/hibernate-orm)

[Related Hibernate with Panache section...](https://quarkus.io/guides/hibernate-orm-panache)


### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)

# Limpar cache corrompido (se necessário)
Stop-Process -Name java -Force -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .\target\quarkus\

# Compilar (sem testes)
.\mvnw.cmd -DskipTests clean package

# Modo dev (hot reload) — recomendado
.\mvnw.cmd quarkus:dev

# Empacotar e executar como jar (produção)
.\mvnw.cmd package
java -jar target\quarkus-app\quarkus-run.jar

# Para über-jar
.\mvnw.cmd package -Dquarkus.package.jar.type=uber-jar
java -jar target\*runner.jar
