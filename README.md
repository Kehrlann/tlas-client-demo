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
- Tanzu Local Authorization Server v0.0.7 or higher,
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

In real-world use-cases, requirements often include authorization based on information about the
logged in user, for example using their roles or attributes. Authorizations can be implemented in
many ways in Spring Security. In this example, provide a `OidcUserService` bean to map an `id_token`
claim to user "roles". These roles can then be used to lock down or open up access. For fine-grained
configuration, provide a `SecurityFilterChain` bean:

```java

@Configuration
class DemoConfiguration {
    @Bean
    OidcUserService oidcUserService() {
        var oidcUserService = new OidcUserService();
        oidcUserService.setOidcUserMapper((oidcUserRequest, oidcUserInfo) -> {
            // Will map the "roles" claim from the `id_token` into user authorities (roles)
            var roles = oidcUserRequest.getIdToken().getClaimAsStringList("roles");
            var authorities = AuthorityUtils.createAuthorityList();
            if (roles != null) {
                roles.stream()
                        .map(r -> "ROLE_" + r)
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }
            return new DefaultOidcUser(authorities, oidcUserRequest.getIdToken(), oidcUserInfo);
        });
        return oidcUserService;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/admin/**").hasRole("admin");
                    auth.anyRequest().authenticated();
                })
                .oauth2Login(Customizer.withDefaults())
                .build();
    }
}
```

Add an `/admin` endpoint that can be consumed in the controller:

```java

@RestController
class DemoController {

    @GetMapping("/")
    String index(@AuthenticationPrincipal OidcUser user) {
        return "Hello " + user.getEmail();
    }

    @GetMapping("/admin")
    String admin() {
        return "Hello, admin!";
    }

}
```


Lastly, configure Tanzu Local Authorization Server, with two users: `alice`, who has an `admin` role
granting her privileges in the client app, and `bob` with a regular, non-admin account:


```yaml

tanzu:
  local-authorization-server:
    users:
      - username: alice
        password: alice-password
        attributes:
          # email is a standard OpenID claim, obtained with the email scope
          email: "alice@example.com"
          # roles is a custom, application-specific claim
          roles:
            - viewer
            - editor
            - admin
      - username: bob
        password: bob-password
        attributes:
          email: bob@example.com
          roles:
            - viewer
            - editor
```

Then, run the Auth Server using this configuration file:

```shell

java -jar tanzu-local-authorization-server-<VERSION>.jar --config=config.yml
```

Now access your client application's newly created admin page at http://localhost:8080/admin : only
`alice` should be able to access the page. Use incognito mode in your browser so switching users is
easy and does not require implementing logout.

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

We would like to test our logic against a running OIDC identity provider. For this, we need to run a web browser. We
could use Selenium and a real browser, but Spring Boot also provides a more lightweight
alternative, [HtmlUnit](https://htmlunit.sourceforge.io/), which emulates a real web browser in Java.

The goal for these tests is to have a "real" user interaction, follow redirects, and ensure that our app is correctly
wired to the AuthServer, gets and parses tokens, etc. We want to test our app with Tanzu Local Authorization Server,
which we will package inside a container.

Start by importing the testcontainers dependencies in your `build.gradle.kts` file, as well as HtmlUnit:

```
testImplementation("org.springframework.boot:spring-boot-testcontainers")
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("net.sourceforge.htmlunit:htmlunit")
```

Copy your local auth server to the appropriate directory, here
`test/resources/tanzu-local-authorization-server/authserver.jar`, for example by running:

```
cp tanzu-local-authorization-server-*.jar test/resources/tanzu-local-authorization-server/authserver.jar
```

Then you can configure Testcontainers tests, as in the sample `TestcontainersLoginTests` test file:

```java

import java.io.IOException;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoApplicationtests {

    private final WebClient webClient = new WebClient();

    @LocalServerPort
    private int localServerPort;

    @BeforeEach
    void setUp() {
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
    }

    @Test
    void aliceIsAdmin() throws IOException {
        HtmlPage loginPage = webClient.getPage("http://localhost:" + localServerPort);

        loginPage.<HtmlInput>querySelector("#username").type("alice");
        loginPage.<HtmlInput>querySelector("#password").type("alice-password");

        HtmlPage loggedInPage = loginPage.<HtmlButton>querySelector("button").click();
        assertThat(loggedInPage.getBody().getTextContent()).isEqualTo("Hello alice@example.com");

        var adminStatusCode = webClient.getPage("http://localhost:" + localServerPort + "/admin").getWebResponse().getStatusCode();
        assertThat(adminStatusCode).isEqualTo(200);
    }

    @Test
    void bobIsNotAdmin() throws IOException {
        HtmlPage loginPage = webClient.getPage("http://localhost:" + localServerPort);

        loginPage.<HtmlInput>querySelector("#username").type("bob");
        loginPage.<HtmlInput>querySelector("#password").type("bob-password");

        HtmlPage loggedInPage = loginPage.<HtmlButton>querySelector("button").click();
        assertThat(loggedInPage.getBody().getTextContent()).isEqualTo("Hello bob@example.com");

        var adminStatusCode = webClient.getPage("http://localhost:" + localServerPort + "/admin").getWebResponse().getStatusCode();
        assertThat(adminStatusCode).isEqualTo(403);
    }

    @TestConfiguration
    static class TestcontainersConfiguration {
        @Bean
        @DynamicPropertySource
        GenericContainer<?> genericContainer(DynamicPropertyRegistry registry) {
            var tanzuAuthServer = new GenericContainer<>("bellsoft/liberica-openjre-alpine:21")
                    .withCopyFileToContainer(
                            // Point Testcontainers to the Tanzu-Local-Authorization-Server release
                            MountableFile.forClasspathResource("tanzu-local-authorization-server/authserver.jar"),
                            "/tanzu-local-authorization-server.jar")
                    .withCopyFileToContainer(MountableFile.forClasspathResource("tanzu-local-authorization-server/config.yml"), "/config.yml")
                    .withCommand("java", "-jar", "/tanzu-local-authorization-server.jar", "--config", "/config.yml")
                    .withExposedPorts(9000);

            registry.add(
                    "spring.security.oauth2.client.provider.tanzu-local-authorization-server.issuer-uri",
                    () -> "http://localhost:" + tanzuAuthServer.getFirstMappedPort()
            );
            return tanzuAuthServer;
        }
    }

}
```

Now that the application is wired to a dockerized authserver, add some tests for the `alice` and `bob` users described
in the previous section:

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
