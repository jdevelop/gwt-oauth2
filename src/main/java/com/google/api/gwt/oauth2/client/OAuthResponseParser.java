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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TokenInfo tokenInfo = (TokenInfo) o;

            if (accessToken != null ? !accessToken.equals(tokenInfo.accessToken) : tokenInfo.accessToken != null)
                return false;
            if (expires != null ? !expires.equals(tokenInfo.expires) : tokenInfo.expires != null) return false;
            if (error != null ? !error.equals(tokenInfo.error) : tokenInfo.error != null) return false;
            if (errorDesc != null ? !errorDesc.equals(tokenInfo.errorDesc) : tokenInfo.errorDesc != null) return false;
            return !(errorUri != null ? !errorUri.equals(tokenInfo.errorUri) : tokenInfo.errorUri != null);

        }

        @Override
        public int hashCode() {
            int result = accessToken != null ? accessToken.hashCode() : 0;
            result = 31 * result + (expires != null ? expires.hashCode() : 0);
            result = 31 * result + (error != null ? error.hashCode() : 0);
            result = 31 * result + (errorDesc != null ? errorDesc.hashCode() : 0);
            result = 31 * result + (errorUri != null ? errorUri.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "TokenInfo{" +
                    "accessToken='" + accessToken + '\'' +
                    ", expires='" + expires + '\'' +
                    ", error='" + error + '\'' +
                    ", errorDesc='" + errorDesc + '\'' +
                    ", errorUri='" + errorUri + '\'' +
                    '}';
        }
    }

    TokenInfo parseResponse(String hash, String queryString);

}
