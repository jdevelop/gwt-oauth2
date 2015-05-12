package com.google.api.gwt.oauth2.client;

/**
 * User: Eugene Dzhurinsky
 * Date: 5/11/15
 */
public class DefaultResponseParser implements OAuthResponseParser {

    private final Auth.Clock clock;

    public DefaultResponseParser(Auth.Clock clock) {
        this.clock = clock;
    }

    public TokenInfo parseResponse(String hash, String queryString) {
        OAuthResponseParser.TokenInfo info = new TokenInfo();

        // Iterate over keys and values in the string hash value to find relevant
        // information like the access token or an error message. The string will be
        // in the form of: #key1=val1&key2=val2&key3=val3 (etc.)
        int idx = 1;
        while (idx < hash.length() - 1) {
            // Grab the next key (between start and '=')
            int nextEq = hash.indexOf('=', idx);
            if (nextEq < 0) {
                break;
            }
            String key = hash.substring(idx, nextEq);

            // Grab the next value (between '=' and '&')
            int nextAmp = hash.indexOf('&', nextEq);
            nextAmp = nextAmp < 0 ? hash.length() : nextAmp;
            String val = hash.substring(nextEq + 1, nextAmp);

            // Start looking from here from now on.
            idx = nextAmp + 1;

            // Store relevant values to be used later.
            if (key.equals("access_token")) {
                info.accessToken = val;
            } else if (key.equals("expires_in")) {
                // expires_in is seconds, convert to milliseconds and add to now
                Double expiresIn = Double.valueOf(val) * 1000;
                info.expires = String.valueOf(clock.now() + expiresIn);
            } else if (key.equals("error")) {
                info.error = val;
            } else if (key.equals("error_description")) {
                info.errorDesc = " (" + val + ")";
            } else if (key.equals("error_uri")) {
                info.errorUri = "; see: " + val;
            }
        }

        return info;

    }

}
