package com.hrapp.rh.securite;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UtilisateurDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7);
            jwtService.readClaims(token)
                    .filter(jwtService::isAccessToken)
                    .ifPresent(claims -> {
                        try {
                            UtilisateurConnecte utilisateur =
                                    userDetailsService.loadUserByUsername(claims.getSubject());
                            if (utilisateur.isEnabled() && jwtService.matchesVersion(claims, utilisateur)) {
                                var authentication = new UsernamePasswordAuthenticationToken(
                                        utilisateur, null, utilisateur.getAuthorities());
                                authentication.setDetails(
                                        new WebAuthenticationDetailsSource().buildDetails(request));
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            }
                        } catch (UsernameNotFoundException ignored) {
                            // compte supprimé : on laisse la requête non authentifiée (401)
                        }
                    });
        }
        chain.doFilter(request, response);
    }
}