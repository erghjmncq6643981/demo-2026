package com.chandler.motivation.security;

import com.chandler.motivation.domain.dataobject.MotivationUser;
import com.chandler.motivation.domain.mapper.MotivationUserMapper;
import com.chandler.motivation.support.MotivationConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final MotivationUserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request.getHeader("Authorization"));
        if (StringUtils.hasText(token)) {
            try {
                JwtClaims claims = jwtTokenService.parse(token);
                MotivationUser user = userMapper.selectById(claims.userId());
                if (user != null
                        && Integer.valueOf(MotivationConstants.Flag.YES).equals(user.getEnabled())
                        && Integer.valueOf(MotivationConstants.Flag.NO).equals(user.getDeleted())) {
                    MotivationUserPrincipal principal = new MotivationUserPrincipal(user);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, token, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return "";
        }
        String value = authorization.trim();
        return value.regionMatches(true, 0, "Bearer ", 0, 7) ? value.substring(7).trim() : value;
    }
}
