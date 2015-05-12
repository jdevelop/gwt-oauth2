package com.google.api.gwt.oauth2.client;

/**
 * User: Eugene Dzhurinsky
 * Date: 5/11/15
 */
public interface OAuthResponseParser {

    /** Encapsulates information an access token and when it will expire. */
    class TokenInfo {
        String accessToken;
        String expires;

        String error = null;
        String errorDesc = "";
        String errorUri = "";

        String asString() {
            return accessToken + "-----" + (expires == null ? "" : expires);
        }

        static TokenInfo fromString(String val) {
            String[] parts = val.split("-----");
            TokenInfo info = new TokenInfo();
            info.accessToken = parts[0];
            info.expires = parts.length > 1 ? parts[1] : null;
            return info;
        }
    }

    TokenInfo parseResponse(String hash, String queryString);

}
