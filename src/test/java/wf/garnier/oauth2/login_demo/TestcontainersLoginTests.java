package wf.garnier.oauth2.login_demo;

import java.io.IOException;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class TestcontainersLoginTests {

    @LocalServerPort
    int port;

    WebClient webClient = new WebClient();

    @Container
    static GenericContainer<?> tanzuAuthServer = new GenericContainer<>("bellsoft/liberica-openjre-alpine:21")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("tanzu-local-authorization-server/authserver.jar"), "/tanzu-local-authorization-server.jar")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("tanzu-local-authorization-server/config.yml"), "/config.yml")
            .withCommand("java", "-jar", "/tanzu-local-authorization-server.jar", "--config", "config.yml")
            .withExposedPorts(9000);

    @BeforeEach
    void setUp() {
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setRedirectEnabled(true);
    }

    @Test
    void isFrenchStaff() throws IOException {
        login("alice", "alice-password");

        HtmlPage indexPage = webClient.getPage("http://localhost:" + port);
        HtmlPage frenchPage = webClient.getPage("http://localhost:" + port + "/fr");

        assertThat(indexPage.getBody().getTextContent()).contains("Hello, alice.spring@example.com");
        assertThat(frenchPage.getBody().getTextContent()).contains("Bonjour, alice.spring@example.com");
    }

    @Test
    void isNotFrenchStaff() throws IOException {
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);

        login("bob", "bob-password");

        HtmlPage indexPage = webClient.getPage("http://localhost:" + port);
        Page frenchPage = webClient.getPage("http://localhost:" + port + "/fr");
        assertThat(indexPage.getBody().getTextContent()).contains("Hello, bob@example.com");
        assertThat(frenchPage.getWebResponse().getStatusCode()).isEqualTo(403);
    }

    @Test
    void logout() throws IOException {
        login("bob", "bob-password");

        HtmlPage indexPage = webClient.getPage("http://localhost:" + port);
        HtmlPage logoutPage = indexPage.getAnchorByText("Logout").click();
        HtmlPage loggedOutIndexPage = logoutPage.<HtmlButton>querySelector("button").click();

        assertThat(loggedOutIndexPage.getBody().getTextContent()).contains("You have been signed out");
    }

    private void login(String username, String password) throws IOException {
        HtmlPage loginPage = webClient.getPage("http://localhost:" + port);

        HtmlInput usernameField = loginPage.getHtmlElementById("username");
        HtmlInput passwordField = loginPage.getHtmlElementById("password");
        HtmlButton loginButton = loginPage.querySelector("button");

        usernameField.type(username);
        passwordField.type(password);

        loginButton.click();
    }

    @DynamicPropertySource
    static void clientRegistrationProperties(DynamicPropertyRegistry registry) {
        // This will configure your Spring Boot app to point to the running container
        registry.add("spring.security.oauth2.client.provider.tanzu-local-authorization-server.issuer-uri",
                () -> "http://localhost:" + tanzuAuthServer.getFirstMappedPort());
    }
}
