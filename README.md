# Tanzu Local Authorization Server demo

This repo is a demo
for [Tanzu Local Authorization Server](https://docs.vmware.com/en/Tanzu-Spring-Runtime/Commercial/Tanzu-Spring-Runtime/index-local-auth-server.html),
or TLAS for short. From the documentation:

> Tanzu Local Authorization Server helps with running real Authorization Server locally, without relying on external
> services (Okta, Azure Entra, ...) or a more heavyweight solutions (Keycloak). It provides sane defaults and just
> enough features to produce access_tokens and id_tokens that look like prod tokens.
>
> Tanzu Local Authorization Server also helps with unit-and-integration testing. By leveraging the experimental Spring
> Boot Testjars project, or packaging it in a Docker image, tests can rely on a fast-starting authserver.

TLAS is part of the [Tanzu Spring enterprise suite](https://enterprise.spring.io/), commercially available.

## Requirements

Required:

- Java 17+
- Tanzu Local Authorization Server,
  see [docs on how to obtain](https://docs.vmware.com/en/Tanzu-Spring-Runtime/Commercial/Tanzu-Spring-Runtime/local-auth-server-about-local-auth-server.html)

Optional:

- Docker (for tests with Testcontainers)

## Running the project

### Booting up Tanzu Local Authorization Server

Download TLAS, and then run it:

```
java -jar tanzu-local-authorization-server-*.jar
```

Notice how it prints help messages in the console. It shows which users available in the system:

```
🧑 You can log in with the following users:
---

- username: user
  password: password
```

It also explains how you can configure a Spring Boot application to use your running TLAS instance as an identity
provider, by adding the following to your `application.yml`:

```
🥾 You can use the following Spring Boot configuration in your own application to target the AuthServer:
---

spring:
  security:
    oauth2:
      client:
        registration:
          tanzu-local-authorization-server:
            client-id: default-client-id
            client-secret: default-client-secret
            scope:
              - openid
              - email
              - profile
        provider:
          tanzu-local-authorization-server:
            issuer-uri: http://localhost:9000
```

You can also access http://localhost:9000 for additional information, and
the [getting started guide](https://docs.vmware.com/en/Tanzu-Spring-Runtime/Commercial/Tanzu-Spring-Runtime/local-auth-server-about-local-auth-server.html)
in the official documentation.

### Running the demo: basic login

Run this project with the `local` profile. You will notice that we use the above configuration snippet in
`application-local.yml` for our demo, so you do not have to do the copy-paste yourself.

From the command line:

```
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Then, navigate to http://localhost:8080 . You will be redirected to TLAS (http://localhost:9000) for log in. Use the
default username and password, `user` / `password`.

You should see a welcome message, **Hello, user@example.com**.

### Running demo: role-based access control (RBAC)

Real-world use-cases often must manage authorization based on information about the logged in user, for example using
their roles or attributes.

You may have noticed that in the main page of our application, there is a link for "French employees
only", http://localhost:8080/fr. If you try to access that link with `user@example.com`, access will be denied with
`HTTP 403 Forbidden`. The rule is that the user must be part of the `France_Staff` group, expressed in our
`SecurityConfiguration` class, through the access control rule:

```java
auth.requestMatchers("/fr/**").access(mustBeFrenchEmployee());
```

With the actual implementation:

```java
private static AuthorizationManager<RequestAuthorizationContext> mustBeFrenchEmployee() {
    return (authSupplier, object) -> {
        OidcUser user = (OidcUser) authSupplier.get().getPrincipal();
        var groups = user.getClaimAsStringList("groups");
        if (groups == null) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(groups.contains("France_Staff"));
    };
}
```

However, our `user@example.com` does not have the `French_Staff` group. We need to configure TLAS so it has users with
that group. You can use the file `tlas-config.yml` in this repo:

```
java -jar tanzu-local-authorization-server-*.jar --config tlas-config.yml
```

The config file has two users, `alice` (pw: `alice-password`), member of `France_Staff`; and `bob` (pw: `bob-password`),
not member of `France_Staff`:

```yaml
tanzu:
  local-authorization-server:
    users:
      - username: alice
        password: alice-password
        attributes:
          email: "alice.spring@example.com"
          # ... omitted ...
          groups:
            - "spring.user"
            - "France_Staff"
      - username: bob
        password: bob-password
        attributes:
          email: bob@example.com
          groups:
            - "spring.user"
            - "UK_Staff"
```

Restart your application, and try navigating to the French employees page logged in as `alice` (works) or `bob` (returns
HTTP 403).

## Using Tanzu Local Authorization Server in tests

There are three options for running tests against a live TLAS instance:

1. Use [Testcontainers](https://testcontainers.com/), to run it in a docker containers
2. Use [Spring Boot Testjars](https://github.com/spring-projects-experimental/spring-boot-testjars) (experimental), to
   run it in a separate java process
3. Launch and manage it manually (e.g. by running `java -jar` on your machine). We do not recommend you do this, it's
   too inconvenient!

We will focus on the first two options. We have written tests for our `alice` and `bob` users described above, using
an [HtmlUnit](https://htmlunit.sourceforge.io/) `WebClient`, that emulates a web browser. You could also use Selenium
and a real browser. The goal is to have a "real" user interaction, follow redirects, and ensure that our app is
correctly wired to the AuthServer, gets and parses tokens, etc. Both our test files `TestcontainersLoginTests` and
`TestjarsLoginTests` have the same tests, just different configuration to run TLAS.

### With Testcontainers

Import the testcontainers dependencies in your `build.gradle.kts` file:

```kotlin
testImplementation("org.springframework.boot:spring-boot-testcontainers")
testImplementation("org.testcontainers:junit-jupiter")
```

Copy your local auth server to the appropriate directory, here
`test/resources/tanzu-local-authorization-server/authserver.jar`, for example by running:

```
cp tanzu-local-authorization-server-*.jar test/resources/tanzu-local-authorization-server/authserver.jar
```

Then you can configure Testcontainers tests, as in the sample `TestcontainersLoginTests` test file:

```java

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class TestcontainersLoginTests {

    // Set up the container by copying the correct files into an OpenJDK container, and then
    // running java -jar authserver.jar
    @Container
    static GenericContainer<?> tanzuAuthServer = new GenericContainer<>("bellsoft/liberica-openjre-alpine:21")
            // copy the authserver jar
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("tanzu-local-authorization-server/authserver.jar"), "/tanzu-local-authorization-server.jar")
            // copy the config file
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("tanzu-local-authorization-server/config.yml"), "/config.yml")
            // Run the launch command
            .withCommand("java", "-jar", "/tanzu-local-authorization-server.jar", "--config", "config.yml")
            // Expose port 9000
            .withExposedPorts(9000);

    @DynamicPropertySource
    static void clientRegistrationProperties(DynamicPropertyRegistry registry) {
        // This will configure your Spring Boot app to point to the running container, with its random port
        registry.add("spring.security.oauth2.client.provider.tanzu-local-authorization-server.issuer-uri",
                () -> "http://localhost:" + tanzuAuthServer.getFirstMappedPort());
    }

    @Test
    void someTest() {
        // ...
    }
}
```

### With Spring Boot Testjars

[Spring Boot Testjars](https://github.com/spring-projects-experimental/spring-boot-testjars) is an experimental project
to run jars and Spring Boot apps as part of tests. It is similar to Testcontainers, but:

1. Does not require Docker
2. Can only run Java code

To run tests with Spring Boot Testjars, add the dependency to `build.gradle.kts`:

```
testImplementation("org.springframework.experimental.boot:spring-boot-testjars:0.0.1")
```

Then configure your test suite, similar to what we do in `TestjarsLoginTests`:

```java

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TestjarsLoginTests {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableDynamicProperty
    static class TestOAuth2Login {

        @Bean
        @OAuth2ClientProviderIssuerUri(providerName = "tanzu-local-authorization-server")
        static CommonsExecWebServerFactoryBean authorizationServer() {
            return CommonsExecWebServerFactoryBean.builder()
                    // when running the tests:
                    .classpath(cp -> {
                                // Add the auth-server jar
                                cp.files(TestjarsLoginTests.class.getClassLoader().getResource("tanzu-local-authorization-server/authserver.jar").getFile());
                                // Add the folder containing the configuration
                                cp.files(TestjarsLoginTests.class.getClassLoader().getResource("tanzu-local-authorization-server/").getFile());
                            }
                    )
                    // Set where TLAS gets its configuration from
                    .addSystemProperties(Map.of("spring.config.location", "classpath:/config.yml"))
                    // Spring Boot launcher has changed between 3.2 and 3.3, so this is required for TLAS for now
                    .mainClass("org.springframework.boot.loader.launch.JarLauncher");
        }

    }

    @Test
    void someTest() {
        // ...
    }

}
```