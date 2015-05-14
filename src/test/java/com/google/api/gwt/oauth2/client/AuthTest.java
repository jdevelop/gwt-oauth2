/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.api.gwt.oauth2.client;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.core.client.testing.StubScheduler;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for {@link Auth}.
 *
 * @author jasonhall@google.com (Jason Hall)
 */
public class AuthTest {

  private MockAuth auth;

  @Before
  public void setUp() throws Exception {
    auth = new MockAuth();
  }

  /**
   * When the request does not have a token stored, the popup is used to get the
   * token.
   */
  @Test
  public void testLogin_noToken() {
    AuthRequest req = new AuthRequest("url", "clientId").withScopes("scope");
    MockCallback callback = new MockCallback();
    auth.login(req, callback);

    // The popup was used and the iframe wasn't.
    assertTrue(auth.loggedInViaPopup);
    assertEquals("url?client_id=clientId&response_type=token&scope=scope&redirect_uri=popup.html",
        auth.lastUrl);
  }

  /**
   * When the token is found in cookies, but may expire soon, the popup will be
   * used to refresh the token.
   */
  @Test
  public void testLogin_expiringSoon() {
    AuthRequest req = new AuthRequest("url", "clientId").withScopes("scope");

    // Storing a token that expires soon (in just under 10 minutes)
    OAuthResponseParser.TokenInfo info = new OAuthResponseParser.TokenInfo();
    info.accessToken = "expired";
    info.expires = String.valueOf(MockClock.now + 10 * 60 * 1000 - 1);
    auth.setToken(req, info);

    MockCallback callback = new MockCallback();
    auth.login(req, callback);

    assertTrue(auth.expiringSoon(info));

    assertTrue(auth.loggedInViaPopup);
    assertEquals("url?client_id=clientId&response_type=token&scope=scope&redirect_uri=popup.html",
        auth.lastUrl);
  }

  /**
   * When the token is found in cookies and will not expire soon, neither popup
   * nor iframe is used, and the token is immediately passed to the callback.
   */
  @Test
  public void testLogin_notExpiringSoon() {
    AuthRequest req = new AuthRequest("url", "clientId").withScopes("scope");

    // Storing a token that does not expire soon (in exactly 10 minutes)
    OAuthResponseParser.TokenInfo info = new OAuthResponseParser.TokenInfo();
    info.accessToken = "notExpiringSoon";
    info.expires = String.valueOf(MockClock.now + 10 * 60 * 1000);
    auth.setToken(req, info);

    MockCallback callback = new MockCallback();
    auth.login(req, callback);

    // A deferred command will have been scheduled. Execute it.
    List<ScheduledCommand> deferred = ((StubScheduler) auth.scheduler).getScheduledCommands();
    assertEquals(1, deferred.size());
    deferred.get(0).execute();

    // The iframe was used and the popup wasn't.
    assertFalse(auth.loggedInViaPopup);

    // onSuccess() was called and onFailure() wasn't.
    assertEquals("notExpiringSoon", callback.token.accessToken);
    assertNull(callback.failure);
  }

  /**
   * When the token is found in cookies and does not specify an expire time, the
   * iframe will be used to refresh the token without displaying the popup.
   */
  @Test
  public void testLogin_nullExpires() {
    AuthRequest req = new AuthRequest("url", "clientId").withScopes("scope");

    // Storing a token with a null expires time
    OAuthResponseParser.TokenInfo info = new OAuthResponseParser.TokenInfo();
    info.accessToken = "longToken";
    info.expires = null;
    auth.setToken(req, info);

    MockCallback callback = new MockCallback();
    auth.login(req, callback);

    // TODO(jasonhall): When Auth supports immediate mode for supporting
    // providers, a null expiration will trigger an iframe immediate-mode
    // refresh. Until then, the popup is always used.
    assertTrue(auth.loggedInViaPopup);
  }

  /**
   * When finish() is called, the callback passed to login() is executed with
   * the correct token, and a cookie is set with relevant information, expiring
   * in the correct amount of time.
   */
  @Test
  public void testFinish() {
    AuthRequest req = new AuthRequest("url", "clientId").withScopes("scope");
    MockCallback callback = new MockCallback();
    auth.login(req, callback);

    // Simulates the auth provider's response
    auth.finish("#access_token=foo&expires_in=10000", "");

    // onSuccess() was called and onFailure() wasn't
    assertEquals("foo", callback.token.accessToken);
    assertNull(callback.failure);

    // A token was stored as a result
    InMemoryTokenStore ts = (InMemoryTokenStore) auth.tokenStore;
    assertEquals(1, ts.store.size());

    // That token is clientId+scope -> foo+expires
    OAuthResponseParser.TokenInfo info = OAuthResponseParser.TokenInfo.fromString(ts.store.get("clientId-----scope"));
    assertEquals("foo", info.accessToken);
    assertNotNull(info.expires);
  }

  /**
   * If finish() is passed a bad hash from the auth provider, a RuntimeException
   * will be passed to the callback.
   */
  @Test
  public void testFinish_badHash() {
    AuthRequest req = new AuthRequest("url", "clientId").withScopes("scope");
    MockCallback callback = new MockCallback();
    auth.login(req, callback);

    // Simulates the auth provider's response
    auth.finish("#foobarbaznonsense", "omgwtf");

    // onFailure() was called with a RuntimeException stating the error.
    assertNotNull(callback.failure);
    assertTrue(callback.failure instanceof RuntimeException);
    assertEquals("Could not find access_token in hash #foobarbaznonsense",
        ((RuntimeException) callback.failure).getMessage());

    // onSuccess() was not called.
    assertNull(callback.token);
  }

  /**
   * If finish() is passed an access token but no expires time, a TokenInfo will
   * be stored without an expiration time. The next time auth is requested, the
   * iframe will be used, see {@link #testLogin_nullExpires()}.
   */
  @Test
  public void testFinish_noExpires() {
    AuthRequest req = new AuthRequest("url", "clientId").withScopes("scope");
    MockCallback callback = new MockCallback();
    auth.login(req, callback);

    // Simulates the auth provider's response
    auth.finish("#access_token=foo", "oops");

    // onSuccess() was called and onFailure() wasn't
    assertEquals("foo", callback.token.accessToken);
    assertNull(callback.failure);

    // A token was stored as a result
    InMemoryTokenStore ts = (InMemoryTokenStore) auth.tokenStore;
    assertEquals(1, ts.store.size());

    // That token is clientId+scope -> foo+expires
    OAuthResponseParser.TokenInfo info = OAuthResponseParser.TokenInfo.fromString(ts.store.get("clientId-----scope"));
    assertEquals("foo", info.accessToken);
    assertNull(info.expires);
  }

  /**
   * If finish() is passed a hash that describes an error condition, a
   * RuntimeException will be passed to onFailure() with the provider's auth
   * string.
   */
  @Test
  public void testFinish_error() {
    AuthRequest req = new AuthRequest("url", "clientId").withScopes("scope");
    MockCallback callback = new MockCallback();
    auth.login(req, callback);

    // Simulates the auth provider's error response, with the error first, last,
    // and in the middle of the hash, and as the only element in the hash. Also
    // finds error descriptions and error URIs.
    assertError(
        callback, "#error=redirect_uri_mismatch", "Error from provider: redirect_uri_mismatch");
    assertError(callback, "#error=redirect_uri_mismatch&foo=bar",
        "Error from provider: redirect_uri_mismatch");
    assertError(callback, "#foo=bar&error=redirect_uri_mismatch",
        "Error from provider: redirect_uri_mismatch");
    assertError(callback, "#foo=bar&error=redirect_uri_mismatch&bar=baz",
        "Error from provider: redirect_uri_mismatch");
    assertError(callback, "#foo=bar&error=redirect_uri_mismatch&error_description=Bad dog!",
        "Error from provider: redirect_uri_mismatch (Bad dog!)");
    assertError(callback, "#foo=bar&error=redirect_uri_mismatch&error_uri=example.com",
        "Error from provider: redirect_uri_mismatch; see: example.com");
    assertError(callback,
        "#foo=bar&error=redirect_uri_mismatch&error_description=Bad dog!&error_uri=example.com",
        "Error from provider: redirect_uri_mismatch (Bad dog!); see: example.com");

    // If the hash contains a key that ends in error, but not error=, the error
    // will be that the hash was malformed
    assertError(callback, "#wxyzerror=redirect_uri_mismatch",
        "Could not find access_token in hash #wxyzerror=redirect_uri_mismatch");
  }

  private void assertError(MockCallback callback, String hash, String error) {
    // Simulates the auth provider's error response.
    auth.finish(hash, "nothing");

    // onFailure() was called with a RuntimeException stating the error.
    assertNotNull(callback.failure);
    assertTrue(callback.failure instanceof RuntimeException);
    assertEquals(error, ((RuntimeException) callback.failure).getMessage());

    // onSuccess() was not called.
    assertNull(callback.token);
  }

  @Test
  public void testExpiresInfo() {
    AuthRequest req = new AuthRequest("url", "clientId").withScopes("scope");
    auth.login(req, new MockCallback());

    // Simulates the auth provider's response (expires in 10s)
    auth.finish("#access_token=foo&expires_in=10", "");

    MockClock.now += 1000; // Fast forward 1s
    assertEquals(9000.0, auth.expiresIn(req), 0.001d);

    MockClock.now += 10000; // Fast forward another 10s
    assertEquals(-1000.0, auth.expiresIn(req), 0.001d);

    // A request that has no corresponding token expires in -1ms 
    AuthRequest newReq = new AuthRequest("another-url", "another-clientId").withScopes("scope");
    assertEquals(Double.NEGATIVE_INFINITY, auth.expiresIn(newReq), 0.001d);
  }

  private static class MockAuth extends Auth {
    private boolean loggedInViaPopup;
    private String lastUrl;

    private static final TokenStore TOKEN_STORE = new InMemoryTokenStore();

    MockAuth() {
      super(TOKEN_STORE, new MockClock(), new MockUrlCodex(), new StubScheduler(), "popup.html");
    }

    @Override
    void doLogin(String authUrl, Callback<OAuthResponseParser.TokenInfo, Throwable> callback) {
      loggedInViaPopup = true;
      lastUrl = authUrl;
    }
  }

  static class MockClock implements Auth.Clock {
    static double now = 5000;

    @Override
    public double now() {
      return now;
    }
  }

  static class MockUrlCodex implements Auth.UrlCodex {
    @Override
    public String encode(String url) {
      return url;
    }

    @Override
    public String decode(String url) {
      return url;
    }
  }

  private static class InMemoryTokenStore implements TokenStore {
    Map<String, String> store = new HashMap<String, String>();

    @Override
    public void set(String key, String value) {
      store.put(key, value);
    }

    @Override
    public String get(String key) {
      return store.get(key);
    }

    @Override
    public void clear() {
      store.clear();
    }
  }

  private static class MockCallback implements Callback<OAuthResponseParser.TokenInfo, Throwable> {
    private OAuthResponseParser.TokenInfo token;
    private Throwable failure;

    @Override
    public void onSuccess(OAuthResponseParser.TokenInfo token) {
      this.token = token;
    }

    @Override
    public void onFailure(Throwable caught) {
      this.failure = caught;
    }
  }
}
