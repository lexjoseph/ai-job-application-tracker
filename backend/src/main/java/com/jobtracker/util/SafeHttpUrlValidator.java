package com.jobtracker.util;

import com.jobtracker.exception.ApiException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Blocks obvious SSRF targets (loopback, RFC1918, link-local, metadata-style hosts) before the
 * server fetches a user-supplied URL. Does not defeat all redirect or DNS rebinding attacks.
 */
public final class SafeHttpUrlValidator {

    private SafeHttpUrlValidator() {}

    public static URI validateAndNormalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "URL is required.");
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "Malformed URL.");
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "invalid_url", "Only http and https URLs are allowed.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "URL must include a host.");
        }
        validateHost(host);
        return uri.normalize();
    }

    public static URI resolveAndValidate(URI current, String locationHeader) {
        if (locationHeader == null || locationHeader.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "Redirect location missing.");
        }
        URI next = current.resolve(URI.create(locationHeader.strip()));
        String scheme = next.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "Redirect must stay http(s).");
        }
        String host = next.getHost();
        if (host == null || host.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "Redirect URL must include a host.");
        }
        validateHost(host);
        return next.normalize();
    }

    private static void validateHost(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(lower) || lower.endsWith(".localhost") || lower.endsWith(".local")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "That host is not allowed.");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress()
                        || addr.isMulticastAddress()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "That host is not allowed.");
                }
                if (addr instanceof Inet4Address inet4 && isBlockedIpv4(inet4.getAddress())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "That host is not allowed.");
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "Could not resolve host.");
        }
    }

    /** RFC1918, CGNAT carrier-grade NAT, etc. */
    private static boolean isBlockedIpv4(byte[] a) {
        if (a.length != 4) {
            return false;
        }
        int b0 = a[0] & 0xff;
        int b1 = a[1] & 0xff;
        if (b0 == 10) {
            return true;
        }
        if (b0 == 127) {
            return true;
        }
        if (b0 == 0) {
            return true;
        }
        if (b0 == 169 && b1 == 254) {
            return true;
        }
        if (b0 == 172 && b1 >= 16 && b1 <= 31) {
            return true;
        }
        if (b0 == 192 && b1 == 168) {
            return true;
        }
        if (b0 == 100 && b1 >= 64 && b1 <= 127) {
            return true;
        }
        return false;
    }
}
