package wf.garnier.oauth2.login_demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/fr/**").access((authSupplier, object) -> {
                        OidcUser user = (OidcUser) authSupplier.get().getPrincipal();
                        var groups = user.getClaimAsStringList("groups");
                        if (groups == null) {
                            return new AuthorizationDecision(false);
                        }
                        return new AuthorizationDecision(groups.contains("France_Staff"));
                    });
                    auth.anyRequest().authenticated();
                })
                .oauth2Login(Customizer.withDefaults())
                .build();
    }
}
