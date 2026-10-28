package bootcamp.hibernate_practical.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var api = PathPatternRequestMatcher.withDefaults().matcher("/books/**");
        var browserLogin = new LoginUrlAuthenticationEntryPoint("/login");
        var unauthorized = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);

        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/csrf", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/h2-console/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/books/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.HEAD, "/books/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/books/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(login -> login.defaultSuccessUrl("/").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .exceptionHandling(errors -> errors
                        .defaultAuthenticationEntryPointFor(unauthorized, api)
                        .defaultAuthenticationEntryPointFor(browserLogin, AnyRequestMatcher.INSTANCE)
                        .accessDeniedHandler((request, response, exception) -> {
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    boolean anonymous = authentication == null || authentication instanceof AnonymousAuthenticationToken;
                    // CSRF runs before authorization: anonymous API writes must also return 401.
                    response.setStatus(api.matches(request) && anonymous ? 401 : 403);
                }))
                // H2's development console cannot add Spring CSRF tokens to its forms.
                // The console is off by default and requires ADMIN when enabled.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
