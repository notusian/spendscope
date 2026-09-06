package com.spendscope.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Makes sure the {@code XSRF-TOKEN} cookie is actually written.
 *
 * <p>Spring Security resolves the CSRF token lazily. For a server rendered
 * form that is fine because rendering the form touches the token. A single
 * page application never touches it server side, so without this filter the
 * cookie the browser needs would never be sent.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Reading the value is what triggers the repository to persist the
            // token and set the response cookie.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
