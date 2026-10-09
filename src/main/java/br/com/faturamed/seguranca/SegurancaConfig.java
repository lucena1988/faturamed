package br.com.faturamed.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.context.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
public class SegurancaConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http, UsuarioService users) throws Exception {
        RequestMatcher api = request -> request.getRequestURI().startsWith("/api/");
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/login.html", "/login.js", "/auth.js", "/login.css", "/styles.css", "/identidade.css", "/painel-medico.css", "/painel-medico.js", "/assets/**", "/favicon.ico", "/api/auth/csrf", "/api/health", "/error").permitAll()
                .requestMatchers("/api/auth/**", "/conta.html", "/conta.js").authenticated()
                .requestMatchers("/api/medico/**", "/portal.html", "/portal.js").hasRole("MEDICO")
                .requestMatchers("/api/**").hasRole("ADMIN")
                .anyRequest().hasRole("ADMIN"))
            .requestCache(cache -> cache.disable())
            .headers(headers -> headers.contentSecurityPolicy(policy -> policy.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request,response,error) -> {
                    if(api.matches(request)) json(response,401,"Sessao expirada. Entre novamente");
                    else new LoginUrlAuthenticationEntryPoint("/login.html").commence(request,response,error);
                })
                .accessDeniedHandler((request,response,error) -> {
                    if(api.matches(request)) json(response,403,"Acesso nao permitido");
                    else {
                        var authentication=SecurityContextHolder.getContext().getAuthentication();
                        if(authentication!=null && authentication.getPrincipal() instanceof UsuarioAutenticado user && user.perfil().equals("MEDICO"))
                            response.sendRedirect("/portal.html");
                        else response.sendError(403,"Acesso nao permitido");
                    }
                }))
            .formLogin(login -> login.loginPage("/login.html").loginProcessingUrl("/api/auth/login")
                .successHandler((request,response,authentication) -> {
                    var user=(UsuarioAutenticado)authentication.getPrincipal(); users.sucesso(user.id());
                    response.setContentType("application/json"); response.getWriter().write("{\"destino\":\""+(user.perfil().equals("ADMIN")?"/":"/portal.html")+"\"}");
                })
                .failureHandler((request,response,error) -> { users.falha(request.getParameter("username")); json(response,401,"Email ou senha invalidos, ou acesso indisponivel. Apos 5 tentativas, aguarde 15 minutos"); })
                .permitAll())
            .logout(logout -> logout.logoutUrl("/api/auth/logout").logoutSuccessHandler((request,response,auth) -> response.setStatus(204)).deleteCookies("JSESSIONID"))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
                    var auth=SecurityContextHolder.getContext().getAuthentication();
                    if(auth!=null && auth.getPrincipal() instanceof UsuarioAutenticado user && !users.sessaoValida(user)) {
                        if(request.getSession(false)!=null) request.getSession(false).invalidate();
                        SecurityContextHolder.clearContext();
                    }
                    chain.doFilter(request,response);
                }
            },UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    private static void json(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"erro\":\""+message+"\"}");
    }
}
