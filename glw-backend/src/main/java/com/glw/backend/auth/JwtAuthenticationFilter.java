package com.glw.backend.auth;

import com.glw.backend.common.ErrorCode;
import com.glw.backend.model.entity.Account;
import com.glw.backend.model.entity.UserView;
import com.glw.backend.repository.AccountRepository;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** 在 SecurityFilterChain 内创建，避免作为 Servlet Filter 再注册一次。 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final SessionStore sessions;
    private final AccountRepository accounts;
    private final SecurityResponseWriter writer;
    private final PublicEndpoints publicEndpoints;

    public JwtAuthenticationFilter(
            JwtService jwt,
            SessionStore sessions,
            AccountRepository accounts,
            SecurityResponseWriter writer,
            PublicEndpoints publicEndpoints) {
        this.jwt = jwt;
        this.sessions = sessions;
        this.accounts = accounts;
        this.writer = writer;
        this.publicEndpoints = publicEndpoints;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return org.springframework.web.cors.CorsUtils.isPreFlightRequest(request)
                || publicEndpoints.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null) {
            writer.write(response, ErrorCode.UNAUTHORIZED);
            return;
        }
        try {
            if (!header.regionMatches(true, 0, "Bearer ", 0, 7) || header.length() > 8192)
                throw new IllegalArgumentException("Invalid authorization header");
            JwtService.Identity identity = jwt.parseAccess(header.substring(7).trim());
            if (!sessions.active(identity.sessionId(), identity.userId(), identity.tokenId()))
                throw new IllegalArgumentException("Revoked session");
            Account account =
                    accounts.findById(identity.userId())
                            .filter(Account::enabled)
                            .orElseThrow(() -> new IllegalArgumentException("Inactive account"));
            var principal = new AuthPrincipal(UserView.from(account), identity.sessionId());
            var authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + account.role())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            writer.write(response, ErrorCode.UNAUTHORIZED);
            return;
        } catch (DataAccessException ex) {
            SecurityContextHolder.clearContext();
            writer.write(response, ErrorCode.SERVICE_UNAVAILABLE);
            return;
        }
        chain.doFilter(request, response);
    }
}
