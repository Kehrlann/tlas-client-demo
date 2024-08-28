package wf.garnier.oauth2.login_demo;

import java.io.IOException;
import java.util.Map;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.experimental.boot.server.exec.CommonsExecWebServerFactoryBean;
import org.springframework.experimental.boot.test.context.EnableDynamicProperty;
import org.springframework.experimental.boot.test.context.OAuth2ClientProviderIssuerUri;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TestjarsLoginTests {

    @LocalServerPort
    int port;

    private final WebClient webClient = new WebClient(BrowserVersion.FIREFOX);

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

    private void login(String username, String password) throws IOException {
        HtmlPage loginPage = webClient.getPage("http://localhost:" + port);

        HtmlInput usernameField = loginPage.getHtmlElementById("username");
        HtmlInput passwordField = loginPage.getHtmlElementById("password");
        HtmlButton loginButton = loginPage.querySelector("button");

        usernameField.type(username);
        passwordField.type(password);

        loginButton.click();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableDynamicProperty
    static class TestOAuth2Login {

        @Bean
        @OAuth2ClientProviderIssuerUri(providerName = "tanzu-local-authorization-server")
        static CommonsExecWebServerFactoryBean authorizationServer() {
            return CommonsExecWebServerFactoryBean.builder()
                    // when running the tests:
                    .classpath(cp -> {
                                // Add the auth-server app
                                cp.files(TestjarsLoginTests.class.getClassLoader().getResource("tanzu-local-authorization-server/authserver.jar").getFile());
                                // Add the folder containing the configuration
                                cp.files(TestjarsLoginTests.class.getClassLoader().getResource("tanzu-local-authorization-server/").getFile());
                            }
                    )
                    // Set the configuration
                    .addSystemProperties(Map.of("spring.config.location", "classpath:/config.yml"))
                    // Spring Boot launcher has changed between 3.2 and 3.3
                    .mainClass("org.springframework.boot.loader.launch.JarLauncher");
        }

    }

}
