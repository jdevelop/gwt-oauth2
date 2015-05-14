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
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;

/**
 * Provides methods to manage authentication flow.
 *
 * @author jasonhall@google.com (Jason Hall)
 */
public abstract class Auth {

  /** Instance of the {@link Auth} to use in a GWT application. */
  public static final Auth get() {
    return AuthImpl.INSTANCE;
  }

  protected OAuthResponseParser responseParser;

  final TokenStore tokenStore;
  private final Clock clock;
  private final UrlCodex urlCodex;
  final Scheduler scheduler;
  String oauthWindowUrl;

  int height = 600;
  int width = 800;

  Auth(TokenStore tokenStore, Clock clock, UrlCodex urlCodex, Scheduler scheduler,
      String oauthWindowUrl) {
    this.tokenStore = tokenStore;
    this.clock = clock;
    this.urlCodex = urlCodex;
    this.scheduler = scheduler;
    this.oauthWindowUrl = oauthWindowUrl;
  }

  private AuthRequest lastRequest;
  private Callback<OAuthResponseParser.TokenInfo, Throwable> lastCallback;

  private static final double TEN_MINUTES = 10 * 60 * 1000;

  /**
   * Request an access token from an OAuth 2.0 provider.
   *
   * <p>
   * If it can be determined that the user has already granted access, and the
   * token has not yet expired, and that the token will not expire soon, the
   * existing token will be passed to the callback.
   * </p>
   *
   * <p>
   * Otherwise, a popup window will be displayed which may prompt the user to
   * grant access. If the user has already granted access the popup will
   * immediately close and the token will be passed to the callback. If access
   * hasn't been granted, the user will be prompted, and when they grant, the
   * token will be passed to the callback.
   * </p>
   *
   * @param req Request for authentication.
   * @param callback Callback to pass the token to when access has been granted.
   */
  public void login(AuthRequest req,  final OAuthResponseParser responseParser, final Callback<OAuthResponseParser.TokenInfo, Throwable> callback) {
    lastRequest = req;
    lastCallback = callback;

    this.responseParser = responseParser;

    String authUrl = req.toUrl(urlCodex) + "&redirect_uri=" + urlCodex.encode(oauthWindowUrl);

    // Try to look up the token we have stored.
    final OAuthResponseParser.TokenInfo info = getToken(req);
    if (info == null || info.expires == null || expiringSoon(info)) {
      // Token wasn't found, or doesn't have an expiration, or is expired or
      // expiring soon. Requesting access will refresh the token.
      doLogin(authUrl, callback);
    } else {
      // Token was found and is good, immediately execute the callback with the
      // access token.

      scheduler.scheduleDeferred(new ScheduledCommand() {
        @Override
        public void execute() {
          callback.onSuccess(info);
        }
      });
    }
  }

  public void login(AuthRequest req, final Callback<OAuthResponseParser.TokenInfo, Throwable> callback) {
    login(req, new DefaultResponseParser(clock), callback);
  }

  /**
   * Returns whether or not the token will be expiring within the next ten
   * minutes.
   */
  boolean expiringSoon(OAuthResponseParser.TokenInfo info) {
    // TODO(jasonhall): Consider varying the definition of "soon" based on the
    // original expires_in value (e.g., "soon" = 1/10th of the total time before
    // it's expired).
    return Double.valueOf(info.expires) < (clock.now() + TEN_MINUTES);
  }

  /**
   * Get the OAuth 2.0 token for which this application may not have already
   * been granted access, by displaying a popup to the user.
   */
  abstract void doLogin(String authUrl, Callback<OAuthResponseParser.TokenInfo, Throwable> callback);

  /**
   * Set the oauth window URL to use to authenticate.
   */
  public Auth setOAuthWindowUrl(String url) {
    this.oauthWindowUrl = url;
    return this;
  }

  /** Sets the height of the OAuth 2.0 popup dialog, in pixels. The default is 600px. */
  public Auth setWindowHeight(int height) {
    this.height = height;
    return this;
  }

  /* Sets the width of the OAuth 2.0 popup dialog, in pixels. The default is 800px. */
  public Auth setWindowWidth(int width) {
    this.width = width;
    return this;
  }

  /**
   * Called by the {@code doLogin()} method which is registered as a global
   * variable on the page.
   */
  // This method is called via a global method defined in AuthImpl.register()
  @SuppressWarnings("unused")
  void finish(String hash, String queryString) {

    OAuthResponseParser.TokenInfo info = responseParser.parseResponse(hash, queryString);

    if (info.error != null) {
      lastCallback.onFailure(
          new RuntimeException("Error from provider: " + info.error + info.errorDesc + info.errorUri));
    } else if (info.accessToken == null) {
      lastCallback.onFailure(new RuntimeException("Could not find access_token in hash " + hash));
    } else {
      setToken(lastRequest, info);
      lastCallback.onSuccess(info);
    }
  }

  /** Test-compatible abstraction for getting the current time. */
  static interface Clock {
    // Using double to avoid longs in GWT, which are slow.
    double now();
  }

  /** Test-compatible URL encoder/decoder. */
  static interface UrlCodex {
    /**
     * URL-encode a string. This is abstract so that the Auth class can be
     * tested.
     */
    String encode(String url);

    /**
     * URL-decode a string. This is abstract so that the Auth class can be
     * tested.
     */
    String decode(String url);
  }

  OAuthResponseParser.TokenInfo getToken(AuthRequest req) {
    String tokenStr = tokenStore.get(req.asString());
    return tokenStr != null ? OAuthResponseParser.TokenInfo.fromString(tokenStr) : null;
  }

  void setToken(AuthRequest req, OAuthResponseParser.TokenInfo info) {
    tokenStore.set(req.asString(), info.asString());
  }

  /**
   * Clears all tokens stored by this class.
   *
   * <p>
   * This will result in subsequent calls to
   * {@link #login(AuthRequest, Callback)} displaying a popup to the user. If
   * the user has already granted access, that popup will immediately close.
   * </p>
   */
  public void clearAllTokens() {
    tokenStore.clear();
  }

  /*
   * @param req The authentication request of which to request the expiration
   *        status.
   * @return The number of milliseconds until the token expires, or negative
   *         infinity if no token was found.
   */
  public double expiresIn(AuthRequest req) {
    String val = tokenStore.get(req.asString());
    return val == null ? Double.NEGATIVE_INFINITY :
        Double.valueOf(OAuthResponseParser.TokenInfo.fromString(val).expires) - clock.now();
  }

}
