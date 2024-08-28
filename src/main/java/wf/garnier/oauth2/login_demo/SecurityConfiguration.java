package wf.garnier.oauth2.login_demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Configuration
@EnableWebSecurity
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        return http
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/fr/**").access(mustBeFrenchEmployee());
                    auth.anyRequest().authenticated();
                })
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout.logoutSuccessHandler(oidcSingleLogout(clientRegistrationRepository)))
                .build();
    }

    private static OidcClientInitiatedLogoutSuccessHandler oidcSingleLogout(ClientRegistrationRepository clientRegistrationRepository) {
        var handler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri("{baseUrl}/login?logout");
        return handler;
    }

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
}
