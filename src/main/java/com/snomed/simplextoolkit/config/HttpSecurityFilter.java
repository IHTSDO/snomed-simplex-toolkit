package com.snomed.simplextoolkit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;

import static java.lang.String.format;

@Component
public class HttpSecurityFilter implements Filter {

	public static final String COOKIE_NAME = "CookieName:";

	@Value("${security-token-header}")
	private String securityTokenHeader;

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		String authToken = extractAuthToken(request, securityTokenHeader);
		if (authToken == null) {
			authToken = UUID.randomUUID().toString();
		}
		SecurityContextHolder.getContext().setAuthentication(new PreAuthenticatedAuthenticationToken(UUID.randomUUID(), authToken));
		filterChain.doFilter(servletRequest, servletResponse);
	}

	private String extractAuthToken(HttpServletRequest request, String securityTokenHeader) {
		if (securityTokenHeader == null) {
			return null;
		} else if (securityTokenHeader.startsWith(COOKIE_NAME)) {
			String cookieName = securityTokenHeader.substring(COOKIE_NAME.length());
			for (Cookie cookie : request.getCookies()) {
				if (cookie.getName().equals(cookieName)) {
					return format("%s=%s", cookieName, cookie.getValue());
				}
			}
		} else {
			return request.getHeader(securityTokenHeader);
		}

		return null;
	}
}
