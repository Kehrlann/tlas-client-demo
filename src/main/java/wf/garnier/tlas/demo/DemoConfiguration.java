package wf.garnier.tlas.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.SecurityFilterChain;

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
