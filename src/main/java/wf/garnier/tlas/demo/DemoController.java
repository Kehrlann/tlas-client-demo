package wf.garnier.tlas.demo;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
