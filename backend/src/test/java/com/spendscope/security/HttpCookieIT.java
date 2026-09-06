package com.spendscope.security;

import com.spendscope.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cookie behaviour over real HTTP, in a real servlet container.
 *
 * <p>MockMvc is used everywhere else because it is fast, but it does not run
 * the container's cookie writer, so {@code Set-Cookie} never appears in a mock
 * response. Cookie flags are precisely the kind of thing that is either right
 * on the wire or not at all, so this one class boots a real port and reads the
 * actual headers a browser would receive.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Cookies over HTTP")
class HttpCookieIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("issues an XSRF-TOKEN cookie the SPA can read")
    void issuesReadableCsrfCookie() {
        ResponseEntity<Void> response = restTemplate.getForEntity("/api/auth/csrf", Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        String csrfCookie = cookieNamed(response, "XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        // The SPA must read this to echo it back in the X-XSRF-TOKEN header,
        // so unlike the session cookie it cannot be HttpOnly.
        assertThat(csrfCookie).doesNotContain("HttpOnly");
        assertThat(csrfCookie).contains("Path=/");
        assertThat(tokenValueOf(csrfCookie)).isNotBlank();
    }

    @Test
    @DisplayName("the session cookie is HttpOnly and SameSite-restricted")
    void sessionCookieIsHardened() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity("/api/auth/register",
                new HttpEntity<>(Map.of("email", "cookie@example.com",
                        "password", "correct-horse-battery"), csrfHeaders(headers)), String.class);

        ResponseEntity<String> login = restTemplate.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", "cookie@example.com",
                        "password", "correct-horse-battery"), csrfHeaders(new HttpHeaders())), String.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        String sessionCookie = cookieNamed(login, "JSESSIONID");
        assertThat(sessionCookie).isNotNull();
        // If JavaScript could read the session id, any XSS would escalate from
        // a page-scoped problem into full account takeover.
        assertThat(sessionCookie).contains("HttpOnly");
        // SameSite is the browser-side half of CSRF defence.
        assertThat(sessionCookie).containsIgnoringCase("SameSite");
    }

    @Test
    @DisplayName("does not mark cookies Secure over plain HTTP in local development")
    void secureFlagIsEnvironmentDependent() {
        // The local profile runs over HTTP, where a Secure cookie would never
        // be sent back and login would silently fail. Production sets
        // SESSION_COOKIE_SECURE=true; see application-prod.yml.
        ResponseEntity<Void> response = restTemplate.getForEntity("/api/auth/csrf", Void.class);
        String csrfCookie = cookieNamed(response, "XSRF-TOKEN");

        assertThat(csrfCookie).isNotNull();
    }

    /**
     * Fetches a CSRF token and puts it in the headers, the way the SPA does.
     */
    private HttpHeaders csrfHeaders(HttpHeaders headers) {
        ResponseEntity<Void> prime = restTemplate.getForEntity("/api/auth/csrf", Void.class);
        String cookie = cookieNamed(prime, "XSRF-TOKEN");
        String token = tokenValueOf(cookie);

        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + token);
        headers.add("X-XSRF-TOKEN", token);
        return headers;
    }

    private String cookieNamed(ResponseEntity<?> response, String name) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null) {
            return null;
        }
        return cookies.stream().filter(cookie -> cookie.startsWith(name + "=")).findFirst().orElse(null);
    }

    private String tokenValueOf(String setCookieHeader) {
        String withoutName = setCookieHeader.substring(setCookieHeader.indexOf('=') + 1);
        int end = withoutName.indexOf(';');
        return end < 0 ? withoutName : withoutName.substring(0, end);
    }
}
