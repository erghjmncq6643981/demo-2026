package com.chandler.learning.agent.security;

import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.identity.infrastructure.mapper.LearningUserMapper;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.security.constant.AuthConstants;
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

    /** 处理当前请求并维护认证或追踪上下文。 */
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

    private String resolveToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return "";
        }
        String value = authorization.trim();
        return value.regionMatches(true, CommonConstants.ZERO,
                AuthConstants.BEARER_PREFIX, CommonConstants.ZERO, AuthConstants.BEARER_PREFIX_LENGTH)
                ? value.substring(AuthConstants.BEARER_PREFIX_LENGTH).trim()
                : value;
    }
}
