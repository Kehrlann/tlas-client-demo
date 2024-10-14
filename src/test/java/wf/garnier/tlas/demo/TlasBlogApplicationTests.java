package wf.garnier.tlas.demo;

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
class TlasBlogApplicationTests {

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
