package com.chandler.learning.agent.security;

import com.chandler.learning.agent.identity.domain.LearningUser;
import com.chandler.learning.agent.identity.infrastructure.LearningUserMapper;
import com.chandler.learning.agent.support.LearningConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 Authorization Bearer 头中解析 JWT，并写入 Spring Security 上下文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final LearningUserMapper userMapper;

    /**
     * 处理 {@code doFilterInternal} 相关业务。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request.getHeader("Authorization"));
        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtClaims claims = jwtTokenService.parse(token);
                LearningUser user = userMapper.selectById(claims.userId());
                if (user != null && Boolean.TRUE.equals(user.getEnabled())) {
                    LearningUserPrincipal principal = new LearningUserPrincipal(user);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            token,
                            principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
                log.debug("JWT 认证失败 path={}", request.getRequestURI());
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 处理 {@code resolveToken} 相关业务。
     */
    private String resolveToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return "";
        }
        String value = authorization.trim();
        return value.regionMatches(true, LearningConstants.ZERO,
                LearningConstants.Auth.BEARER_PREFIX, LearningConstants.ZERO, LearningConstants.Auth.BEARER_PREFIX_LENGTH)
                ? value.substring(LearningConstants.Auth.BEARER_PREFIX_LENGTH).trim()
                : value;
    }
}
