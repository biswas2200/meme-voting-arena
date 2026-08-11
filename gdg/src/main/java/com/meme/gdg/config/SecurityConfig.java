package com.meme.gdg.config;

import com.meme.gdg.security.AuthEntryPointJwt;
import com.meme.gdg.security.AuthTokenFilter;
import com.meme.gdg.service.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(List.of(authenticationProvider()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEV / DOCKER-DEV security chain — relaxed for local development
    // Uses PostgreSQL (Docker), no H2 console needed.
    // All endpoints open so development is frictionless.
    // JWT filter still runs so token-based endpoints work when token is present.
    // ─────────────────────────────────────────────────────────────────────────
    @Bean
    @Profile({"dev", "docker-dev"})
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().permitAll()
            );

        http.authenticationProvider(authenticationProvider());
        // Parse JWT when present — populates Authentication for profile/protected endpoints.
        // All requests still pass through (no 401s in dev).
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRODUCTION security chain — strict JWT enforcement
    // Only public endpoints are open; everything else requires a valid token
    // ─────────────────────────────────────────────────────────────────────────
    @Bean
    @Profile("prod")
    public SecurityFilterChain prodFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers("/api/auth/signin").permitAll()
                .requestMatchers("/api/auth/signup").permitAll()
                // Public read endpoints
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/memes").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/memes/leaderboard").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/memes/battle").permitAll()
                // Public Battle Arena endpoints
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/battle/tournaments").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/battle/tournaments/{id}").permitAll()
                // Actuator health (for load balancer health checks)
                .requestMatchers("/actuator/health").permitAll()
                // Static file uploads
                .requestMatchers("/uploads/**").permitAll()
                // WebSocket endpoint
                .requestMatchers("/ws/**").permitAll()
                // Authenticated meme write endpoints
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/memes").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/memes/upload").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/memes/{id}/vote").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/memes/{id}").authenticated()
                // Authenticated battle endpoints
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/battle/quick/pair").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/battle/vote/quick").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/battle/vote/tournament").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/battle/tournaments").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/battle/tournaments/my").authenticated()
                // Authenticated auth profile endpoints
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/auth/profile").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/auth/profile").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/profile/avatar").authenticated()
                // Admin-only endpoints
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/battle/tournaments/pending").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/battle/tournaments/{id}/approve").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/battle/tournaments/{id}/reject").hasRole("ADMIN")
                // Everything else requires authentication
                .anyRequest().authenticated()
            );

        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
