package wf.garnier.oauth2.login_demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @GetMapping(value = "/", produces = MimeTypeUtils.TEXT_HTML_VALUE)
    public String hello(@AuthenticationPrincipal OidcUser oidcUser) {
        logger.info("""
                
                Using id_token:
                
                %s
                
                """.formatted(oidcUser.getIdToken().getTokenValue())
        );
        return """
                <h1>Hello, %s</h1>
                <p><a href="/fr">French employees only</a></p>
                <p><a href="/logout">Logout</a></p>
                """.formatted(oidcUser.getEmail());
    }


    @GetMapping(value = "/fr", produces = MimeTypeUtils.TEXT_HTML_VALUE)
    public String bonjour(@AuthenticationPrincipal OidcUser oidcUser) {
        return """
                <h1>Bonjour, %s</h1>
                <p><a href="/">Home</a></p>
                <p><a href="/logout">Logout</a></p>
                """.formatted(oidcUser.getEmail());
    }
}
