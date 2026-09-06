package com.spendscope.support;

import org.springframework.mock.web.MockHttpSession;

/**
 * A registered, logged-in user for a test, together with the session that
 * proves it.
 *
 * <p>Every authenticated request in the suite carries one of these sessions, so
 * the tests exercise the same session-cookie path a browser does rather than
 * bypassing security with a mocked principal.
 */
public record TestUser(Long id, String email, String password, MockHttpSession session) {
}
