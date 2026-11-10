package br.com.faturamed.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
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
    /** Pagina inicial da equipe interna apos o login (null para perfis com portal proprio). */
    public static String destinoInterno(String perfil) {
        return switch (perfil) { case "ADMIN", "OPERADOR", "DEV" -> "/inicio.html"; default -> null; };
    }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http, UsuarioService users, br.com.faturamed.conciliacao.MedicoCadastroService medicos) throws Exception {
        RequestMatcher api = request -> request.getRequestURI().startsWith("/api/");
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/login.html", "/login.js", "/auth.js", "/login.css", "/tokens.css", "/styles.css", "/identidade.css", "/tema.css", "/tema.js", "/painel-medico.css", "/painel-medico.js", "/assets/**", "/favicon.ico", "/api/auth/csrf", "/api/health", "/error",
                        "/convite.html", "/cadastro-medico.js", "/cadastro-medico.css", "/api/convites/**",
                        "/esqueci-senha.html", "/esqueci-senha.js", "/api/auth/recuperacao", "/api/auth/recuperacao/confirmar").permitAll()
                .requestMatchers("/api/usuarios/**", "/api/usuarios", "/usuarios.html", "/usuarios.js").hasAnyRole("ADMIN", "DEV", "OPERADOR")
                .requestMatchers("/api/chamados", "/api/chamados/**", "/chamados-widget.js", "/chamados-widget.css").hasAnyRole("ADMIN", "DEV", "OPERADOR")
                .requestMatchers("/api/auth/**", "/conta.html", "/conta.js", "/notificacoes.js", "/notificacoes.css", "/chamados-widget.js", "/chamados-widget.css").authenticated()
                .requestMatchers("/api/medico/**", "/completar-cadastro.html", "/portal.html", "/portal.js", "/conferencia-portal.js", "/extrato-portal.js", "/extrato-portal.css").hasRole("MEDICO")
                .requestMatchers("/api/empresa/**", "/empresa.html", "/empresa.js", "/empresa.css").hasRole("EMPRESA")
                .requestMatchers("/api/hospital-portal/**", "/hospital.html", "/hospital.js", "/hospital.css").hasRole("HOSPITAL")
                .requestMatchers("/ui.css", "/ui.js", "/modulos.css", "/vidro.css", "/temas.css", "/efeitos.css", "/efeitos.js", "/icones3d.css", "/tatil.css", "/tatil.js").authenticated()
                .requestMatchers("/chamados.html", "/chamados.js", "/chamados.css", "/api/**").hasAnyRole("ADMIN", "DEV", "OPERADOR")
                .anyRequest().hasAnyRole("ADMIN", "DEV", "OPERADOR"))
            .requestCache(RequestCacheConfigurer::disable)
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
                        else if(authentication!=null && authentication.getPrincipal() instanceof UsuarioAutenticado user && user.perfil().equals("EMPRESA"))
                            response.sendRedirect("/empresa.html");
                        else if(authentication!=null && authentication.getPrincipal() instanceof UsuarioAutenticado user && user.perfil().equals("HOSPITAL"))
                            response.sendRedirect("/hospital.html");
                        else response.sendError(403,"Acesso nao permitido");
                    }
                }))
            .formLogin(login -> login.loginPage("/login.html").loginProcessingUrl("/api/auth/login")
                .successHandler((request,response,authentication) -> {
                    var user=(UsuarioAutenticado)Objects.requireNonNull(authentication.getPrincipal()); users.sucesso(user.id());
                    String destino = switch (user.perfil()) {
                        case "ADMIN", "OPERADOR", "DEV" -> destinoInterno(user.perfil());
                        case "EMPRESA" -> "/empresa.html";
                        case "HOSPITAL" -> "/hospital.html";
                        case "MEDICO" -> medicos.cadastroCompleto(user.medicoId()) ? "/portal.html" : "/completar-cadastro.html";
                        default -> "/portal.html";
                    };
                    response.setContentType("application/json"); response.getWriter().write("{\"destino\":\""+destino+"\"}");
                })
                .failureHandler((request,response,error) -> { users.falha(request.getParameter("username")); json(response,401,"Email ou senha invalidos, ou acesso indisponivel. Apos 5 tentativas, aguarde 15 minutos"); })
                .permitAll())
            .logout(logout -> logout.logoutUrl("/api/auth/logout").logoutSuccessHandler((request,response,auth) -> response.setStatus(204)).deleteCookies("JSESSIONID"))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(@NonNull HttpServletRequest request,@NonNull HttpServletResponse response,@NonNull FilterChain chain) throws ServletException,IOException {
                    var auth=SecurityContextHolder.getContext().getAuthentication();
                    if(auth!=null && auth.getPrincipal() instanceof UsuarioAutenticado user && !users.sessaoValida(user)) {
                        if(request.getSession(false)!=null) request.getSession(false).invalidate();
                        SecurityContextHolder.clearContext();
                    }
                    // Medico com cadastro incompleto so acessa a tela de completar cadastro.
                    auth=SecurityContextHolder.getContext().getAuthentication();
                    String caminho=request.getRequestURI();
                    if(auth!=null && auth.getPrincipal() instanceof UsuarioAutenticado user && "MEDICO".equals(user.perfil()) && user.medicoId()!=null
                            && (caminho.startsWith("/api/medico/") && !caminho.equals("/api/medico/cadastro") || caminho.equals("/portal.html"))
                            && !medicos.cadastroCompleto(user.medicoId())) {
                        if(api.matches(request)) json(response,403,"Complete seu cadastro para acessar o portal");
                        else response.sendRedirect("/completar-cadastro.html");
                        return;
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
