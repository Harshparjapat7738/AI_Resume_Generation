package ai.careerforge.jd.security;

import ai.careerforge.common.error.ApiException;
import ai.careerforge.common.error.ErrorCode;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Validates that a URL is safe to fetch server-side before jd-service ever opens a
 * connection to it. Job description URLs are supplied by an authenticated user about a
 * page they want summarised — but the fetch itself runs on the internal network, so a
 * malicious URL could otherwise be used to probe or reach internal services (SSRF).
 *
 * <p>Every redirect hop is re-validated through this same guard (see {@link
 * ai.careerforge.jd.fetch.JdUrlFetcher}) — validating only the URL the user typed and then
 * blindly following redirects would let a public URL redirect to a private one.
 *
 * <p><strong>Known limitation (documented, not hidden):</strong> this resolves DNS once and
 * validates the resolved addresses, then connects using the hostname a moment later. A
 * DNS-rebinding attacker who controls both the DNS record and the timing could in theory
 * swap the answer between validation and connection. Full mitigation requires pinning the
 * connection to the exact validated address (not just re-resolving the hostname), which the
 * JDK's built-in {@code HttpClient} does not expose a simple API for. Given the caller is an
 * authenticated user submitting a URL about themselves — not an anonymous, adversarial
 * input — this residual risk is accepted for now rather than hidden behind a false sense of
 * completeness.
 */
public final class SsrfGuard {

    private static final int MAX_REDIRECTS = 5;

    private SsrfGuard() {
    }

    /** @return the validated {@link URI}. Throws {@code JD_URL_BLOCKED} if unsafe. */
    public static URI validate(String rawUrl) {
        URI uri = parse(rawUrl);
        requireHttpScheme(uri);
        requireStandardPort(uri);
        requireHost(uri);
        requireSafeAddresses(resolve(uri.getHost()));
        return uri;
    }

    public static int maxRedirects() {
        return MAX_REDIRECTS;
    }

    private static URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw blocked("A URL is required.");
        }
        try {
            URI uri = new URI(rawUrl.trim());
            if (!uri.isAbsolute()) {
                throw blocked("The URL must be absolute (include https://).");
            }
            return uri;
        } catch (URISyntaxException ex) {
            throw blocked("That doesn't look like a valid URL.");
        }
    }

    private static void requireHttpScheme(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw blocked("Only http and https URLs are supported.");
        }
    }

    private static void requireStandardPort(URI uri) {
        int port = uri.getPort();
        if (port == -1) {
            return; // default port for the scheme
        }
        boolean isStandardHttp = "http".equalsIgnoreCase(uri.getScheme()) && port == 80;
        boolean isStandardHttps = "https".equalsIgnoreCase(uri.getScheme()) && port == 443;
        if (!isStandardHttp && !isStandardHttps) {
            throw blocked("Non-standard ports are not permitted.");
        }
    }

    private static void requireHost(URI uri) {
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw blocked("The URL must include a host.");
        }
    }

    private static InetAddress[] resolve(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw blocked("Could not resolve that host.");
            }
            return addresses;
        } catch (UnknownHostException ex) {
            throw blocked("Could not resolve that host.");
        }
    }

    private static void requireSafeAddresses(InetAddress[] addresses) {
        for (InetAddress address : addresses) {
            if (isUnsafe(address)) {
                throw blocked("That URL points at a private or internal network address.");
            }
        }
    }

    /** Rejects loopback, link-local (includes the 169.254.169.254 cloud metadata endpoint),
     *  site-local/RFC1918 private ranges, multicast, wildcard, and IPv6 unique-local. */
    private static boolean isUnsafe(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || address.isAnyLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            // 0.0.0.0/8 ("this network") and 100.64.0.0/10 (carrier-grade NAT).
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            return first == 0 || (first == 100 && second >= 64 && second <= 127);
        }
        // IPv6 unique local addresses, fc00::/7 — isSiteLocalAddress() does not cover these.
        return (bytes[0] & 0xFE) == 0xFC;
    }

    private static ApiException blocked(String message) {
        return new ApiException(ErrorCode.JD_URL_BLOCKED, message);
    }
}
