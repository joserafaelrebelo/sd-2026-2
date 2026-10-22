package com.sd.rateio.gateway.config;

import com.sd.rateio.gateway.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuração de segurança HTTP do API Gateway.
 * - Desabilita CSRF (API stateless)
 * - Sessão stateless (JWT)
 * - Libera rotas públicas (auth, arquivos estáticos)
 * - Exige autenticação para /api/**
 * - Configura CORS permissivo para desenvolvimento
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Desabilita CSRF — API REST stateless não utiliza cookies de sessão
                .csrf(csrf -> csrf.disable())

                // Habilita CORS com a configuração definida abaixo
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Sessão stateless — sem HttpSession no servidor
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Tratamento semântico de erros de autenticação: 401 Unauthorized imediato na borda
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write("{\"success\":false,\"message\":\"Acesso negado: Token JWT ausente ou inválido (401 Unauthorized)\"}");
                        })
                )

                // Regras de autorização
                .authorizeHttpRequests(auth -> auth
                        // Rotas públicas: página inicial, assets e endpoints de autenticação
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()

                        // Todas as demais rotas /api/** exigem autenticação
                        .requestMatchers("/api/**").authenticated()

                        // Qualquer outra rota (recursos estáticos adicionais) é liberada
                        .anyRequest().permitAll()
                )

                // Insere o filtro JWT antes do filtro padrão de autenticação
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuração CORS permissiva para ambiente de desenvolvimento.
     * Permite qualquer origem, método e header.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
