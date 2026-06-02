package pl.fortaco.opshub.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/api/auth/**").authenticated()
                .requestMatchers("/api/**", "/exports/**", "/uploads/**").authenticated()
                .requestMatchers("/h2-console/**").permitAll()
                .anyRequest().permitAll())
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value()))
                .failureHandler((request, response, exception) -> response.sendError(HttpStatus.UNAUTHORIZED.value(), "Bad credentials")))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value())))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> response.sendError(HttpStatus.UNAUTHORIZED.value())));

        return http.build();
    }

    @Bean
    UserDetailsService users(PasswordEncoder passwordEncoder, @Value("${opshub.security.password:opshub}") String password) {
        return new InMemoryUserDetailsManager(
            User.withUsername("operator")
                .password(passwordEncoder.encode(password))
                .roles("OPERATOR")
                .build(),
            User.withUsername("lider")
                .password(passwordEncoder.encode(password))
                .roles("LEADER", "OPERATOR")
                .build()
        );
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://127.0.0.1:5173", "http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
