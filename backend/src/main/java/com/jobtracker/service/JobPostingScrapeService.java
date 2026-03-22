package com.jobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.response.JobPostingScrapeResponse;
import com.jobtracker.exception.ApiException;
import com.jobtracker.util.SafeHttpUrlValidator;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class JobPostingScrapeService {

    private static final int MAX_BODY_BYTES = 512_000;
    private static final int MAX_JSON_BYTES = 256_000;
    private static final int MAX_REDIRECTS = 5;
    private static final URI GREENHOUSE_API_BASE = URI.create("https://boards-api.greenhouse.io");

    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final Pattern PIPE_SPLIT = Pattern.compile("\\s*\\|\\s*");
    private static final Pattern AT_SPLIT = Pattern.compile("\\s+at\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCENTURE_TRAILING_REF = Pattern.compile("\\s*-\\s*\\d{5,}\\s*$");
    private static final Pattern AMAZON_JOB_ID = Pattern.compile("^(.+?)\\s*-\\s*Job ID:\\s*\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATH_JOBS_NUM = Pattern.compile("/jobs/(\\d+)(?:/|$|\\?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUERY_JOB_ID = Pattern.compile("(?:^|[?&])jobId=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUERY_GH_JID = Pattern.compile("(?:^|[?&])gh_jid=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAREERS_SUBDOMAIN = Pattern.compile("^careers\\.([^.]+)\\..+");
    private static final Pattern JOBS_SUBDOMAIN = Pattern.compile("^jobs\\.([^.]+)\\..+");

    /** Known hosts → display company when HTML is thin (SPAs) or meta is generic. */
    private static final Map<String, String> HOST_TO_COMPANY = new LinkedHashMap<>();

    static {
        HOST_TO_COMPANY.put("amazon.jobs", "Amazon");
        HOST_TO_COMPANY.put("www.amazon.jobs", "Amazon");
        HOST_TO_COMPANY.put("accenture.com", "Accenture");
        HOST_TO_COMPANY.put("www.accenture.com", "Accenture");
        HOST_TO_COMPANY.put("lenovo.com", "Lenovo");
        HOST_TO_COMPANY.put("jobs.lenovo.com", "Lenovo");
        HOST_TO_COMPANY.put("www.lenovo.com", "Lenovo");
        HOST_TO_COMPANY.put("duolingo.com", "Duolingo");
        HOST_TO_COMPANY.put("careers.duolingo.com", "Duolingo");
    }

    private final ObjectMapper objectMapper;

    public JobPostingScrapeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JobPostingScrapeResponse scrape(String rawUrl) {
        URI uri = SafeHttpUrlValidator.validateAndNormalize(rawUrl);
        String html = fetchHtml(uri);
        return extractMetadata(html, uri);
    }

    private String fetchHtml(URI startUri) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        URI current = startUri;
        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .GET()
                    .build();
            try {
                HttpResponse<InputStream> response =
                        client.send(request, BodyHandlers.ofInputStream());
                int code = response.statusCode();
                if (code >= 300 && code < 400) {
                    Optional<String> loc = response.headers().firstValue("location");
                    if (loc.isEmpty()) {
                        throw new ApiException(
                                HttpStatus.BAD_GATEWAY, "fetch_failed", "Redirect without Location header.");
                    }
                    response.body().close();
                    current = SafeHttpUrlValidator.resolveAndValidate(current, loc.get());
                    continue;
                }
                if (code < 200 || code >= 400) {
                    response.body().close();
                    throw new ApiException(
                            HttpStatus.BAD_GATEWAY,
                            "fetch_failed",
                            "Page returned HTTP " + code + ".");
                }

                byte[] body = readLimited(response.body(), MAX_BODY_BYTES);
                if (body.length == 0) {
                    boolean wafChallenge = response.headers()
                            .firstValue("x-amzn-waf-action")
                            .map(v -> v.toLowerCase(Locale.ROOT).contains("challenge"))
                            .orElse(false);
                    if (wafChallenge || code == 202) {
                        throw new ApiException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "listing_blocked",
                                "This job site uses bot protection (for example AWS WAF), so the listing "
                                        + "cannot be fetched from the server. Paste the URL to keep it for reference, "
                                        + "then enter the company and role manually.");
                    }
                    throw new ApiException(
                            HttpStatus.BAD_GATEWAY,
                            "fetch_failed",
                            "The page returned no HTML to parse (HTTP " + code + ").");
                }
                return new String(body, StandardCharsets.UTF_8);
            } catch (ApiException e) {
                throw e;
            } catch (Exception e) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY, "fetch_failed", "Could not download the page.");
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "Too many redirects.");
    }

    private static byte[] readLimited(InputStream in, int maxBytes) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        while (true) {
            int n = in.read(buf);
            if (n < 0) {
                break;
            }
            total += n;
            if (total > maxBytes) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY, "fetch_failed", "Page is too large to parse.");
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private JobPostingScrapeResponse extractMetadata(String html, URI pageUri) {
        String canonicalUrl = pageUri.toString();
        String host = pageUri.getHost() == null ? "" : pageUri.getHost().toLowerCase(Locale.ROOT);

        Optional<Extracted> fromGreenhouse = tryGreenhousePublicApi(pageUri);

        Document doc = Jsoup.parse(html, canonicalUrl);
        Optional<Extracted> fromLd = tryJsonLd(doc);

        String ogTitle = meta(doc, "og:title");
        String ogSite = meta(doc, "og:site_name");
        String twitterTitle = meta(doc, "twitter:title");

        String role = null;
        String company = null;

        if (fromGreenhouse.isPresent()) {
            role = emptyToNull(fromGreenhouse.get().title());
            company = emptyToNull(fromGreenhouse.get().company());
        }

        if (fromLd.isPresent()) {
            if (role == null) {
                role = emptyToNull(fromLd.get().title());
            }
            if (company == null) {
                company = emptyToNull(fromLd.get().company());
            }
        }

        if (host.contains("amazon.jobs")) {
            if (role == null && ogTitle != null) {
                role = cleanRole(ogTitle);
            }
            if (company == null) {
                company = "Amazon";
            }
        }

        if (role == null && twitterTitle != null && !twitterTitle.isBlank()) {
            role = cleanRole(stripTrailingSiteNoise(twitterTitle));
        }
        if (role == null && ogTitle != null && !ogTitle.isBlank()) {
            role = cleanRole(stripTrailingSiteNoise(ogTitle));
        }

        if (company == null && ogSite != null && !ogSite.isBlank()) {
            company = normalizeEmployerName(ogSite);
        }

        if (host.contains("accenture")) {
            String docTitle = doc.title();
            if (!docTitle.isBlank()) {
                if (role == null) {
                    role = cleanRole(accentureStyleTitle(docTitle));
                }
                if (company == null) {
                    company = "Accenture";
                }
            }
        }

        if (role == null) {
            String docTitle = doc.title();
            if (!docTitle.isBlank()) {
                ParsedTitle parsed = parseTitleTag(docTitle, host);
                role = emptyToNull(parsed.role());
                if (company == null) {
                    company = emptyToNull(parsed.company());
                }
            }
        }

        if (role == null || role.length() < 2) {
            String h1 = firstMeaningfulH1(doc);
            if (h1 != null) {
                role = cleanRole(h1);
            }
        }

        if (company == null || company.isBlank()) {
            company = companyFromKnownHost(host);
        }

        if (role != null) {
            role = refineRoleForAmazonTitle(role, host);
            role = accentureStyleTitle(role);
        }
        if (company != null) {
            company = normalizeEmployerName(company);
        }

        String hint = null;
        if (role == null || role.isBlank()) {
            role = "Unknown role";
            hint = "Could not detect the job title; please edit it.";
        }
        if (company == null || company.isBlank()) {
            company = "Unknown company";
            if (hint == null) {
                hint = "Could not detect the company; please edit it.";
            } else {
                hint = "Could not detect company or role reliably; please edit both.";
            }
        }

        if (html.length() < 500 && host.contains("lenovo")) {
            hint = (hint == null ? "" : hint + " ")
                    + "This site often loads the job title in the browser only; Lenovo is prefilled from the domain—add the role manually if needed.";
        }

        return new JobPostingScrapeResponse(company, role, canonicalUrl, hint);
    }

    /**
     * Greenhouse exposes a public read-only JSON API. We only call {@code boards-api.greenhouse.io} with
     * board + id we derive ourselves (no user-controlled host) — safe from SSRF.
     */
    private Optional<Extracted> tryGreenhousePublicApi(URI pageUri) {
        String host = pageUri.getHost();
        if (host == null) {
            return Optional.empty();
        }
        String h = host.toLowerCase(Locale.ROOT);
        String jobId = extractGreenhouseStyleJobId(pageUri);
        if (jobId == null) {
            return Optional.empty();
        }
        String board = inferGreenhouseBoardToken(h);
        if (board == null) {
            return Optional.empty();
        }

        URI apiUri = GREENHOUSE_API_BASE.resolve("/v1/boards/" + board + "/jobs/" + jobId);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(6))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest req = HttpRequest.newBuilder(apiUri)
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = client.send(req, BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                resp.body().close();
                return Optional.empty();
            }
            byte[] raw = readLimited(resp.body(), MAX_JSON_BYTES);
            JsonNode root = objectMapper.readTree(raw);
            String title = text(root, "title");
            String companyName = text(root, "company_name");
            if (title == null && companyName == null) {
                return Optional.empty();
            }
            return Optional.of(new Extracted(title, companyName));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static String extractGreenhouseStyleJobId(URI pageUri) {
        String query = pageUri.getRawQuery();
        if (query != null) {
            Matcher m = QUERY_GH_JID.matcher(query);
            if (m.find()) {
                return m.group(1);
            }
            m = QUERY_JOB_ID.matcher("?" + query);
            if (m.find()) {
                return m.group(1);
            }
        }
        String path = pageUri.getPath();
        if (path != null) {
            Matcher m = PATH_JOBS_NUM.matcher(path + "/");
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static String inferGreenhouseBoardToken(String hostLower) {
        Matcher c = CAREERS_SUBDOMAIN.matcher(hostLower);
        if (c.matches()) {
            return c.group(1);
        }
        Matcher j = JOBS_SUBDOMAIN.matcher(hostLower);
        if (j.matches()) {
            return j.group(1);
        }
        return null;
    }

    private static String refineRoleForAmazonTitle(String role, String hostLower) {
        if (!hostLower.contains("amazon.jobs")) {
            return role;
        }
        Matcher m = AMAZON_JOB_ID.matcher(role);
        if (m.find()) {
            return cleanRole(m.group(1));
        }
        return role;
    }

    private static String accentureStyleTitle(String title) {
        if (title == null || title.isBlank()) {
            return title;
        }
        String t = title.strip();
        t = ACCENTURE_TRAILING_REF.matcher(t).replaceFirst("").strip();
        t = t.replaceAll("(?i)(accenture)\\1+", "$1");
        return t.strip();
    }

    private static String stripTrailingSiteNoise(String title) {
        String t = title.strip();
        t = PIPE_SPLIT.splitAsStream(t).findFirst().orElse(t).strip();
        return t;
    }

    private static String normalizeEmployerName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.strip();
        if (s.equalsIgnoreCase("Amazon.jobs") || s.equalsIgnoreCase("amazon jobs")) {
            return "Amazon";
        }
        if (s.toLowerCase(Locale.ROOT).contains("accenture")) {
            return "Accenture";
        }
        return s;
    }

    private static String companyFromKnownHost(String hostLower) {
        if (hostLower.isEmpty()) {
            return null;
        }
        if (HOST_TO_COMPANY.containsKey(hostLower)) {
            return HOST_TO_COMPANY.get(hostLower);
        }
        for (Map.Entry<String, String> e : HOST_TO_COMPANY.entrySet()) {
            if (hostLower.endsWith("." + e.getKey()) || hostLower.equals(e.getKey())) {
                return e.getValue();
            }
        }
        for (Map.Entry<String, String> e : HOST_TO_COMPANY.entrySet()) {
            if (hostLower.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String firstMeaningfulH1(Document doc) {
        for (Element h1 : doc.select("h1")) {
            if (inChromeExcludedRegion(h1)) {
                continue;
            }
            String text = h1.text().strip();
            if (text.length() >= 3 && text.length() <= 200) {
                return text;
            }
        }
        return null;
    }

    private static boolean inChromeExcludedRegion(Element el) {
        for (Element p : el.parents()) {
            String tag = p.tagName();
            if ("header".equals(tag) || "nav".equals(tag) || "footer".equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private Optional<Extracted> tryJsonLd(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            String data = script.data();
            if (data == null || data.isBlank()) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(data);
                List<JsonNode> nodes = new ArrayList<>();
                if (root.isArray()) {
                    root.forEach(nodes::add);
                } else if (root.has("@graph")) {
                    JsonNode graph = root.get("@graph");
                    if (graph.isArray()) {
                        graph.forEach(nodes::add);
                    } else if (graph.isObject()) {
                        nodes.add(graph);
                    }
                } else {
                    nodes.add(root);
                }
                for (JsonNode n : nodes) {
                    if (isJobPosting(n)) {
                        String title = text(n, "title");
                        String orgName = hiringOrganizationName(n.get("hiringOrganization"));
                        if (title != null || orgName != null) {
                            return Optional.of(new Extracted(title, orgName));
                        }
                    }
                }
            } catch (Exception ignored) {
                // try next script block
            }
        }
        return Optional.empty();
    }

    private static String hiringOrganizationName(JsonNode org) {
        if (org == null || org.isNull()) {
            return null;
        }
        if (org.isObject()) {
            return text(org, "name");
        }
        if (org.isTextual()) {
            String s = org.asText().strip();
            return s.isEmpty() ? null : s;
        }
        return null;
    }

    private static boolean isJobPosting(JsonNode n) {
        JsonNode type = n.get("@type");
        if (type == null || type.isNull()) {
            return false;
        }
        if (type.isTextual()) {
            return "JobPosting".equalsIgnoreCase(type.asText());
        }
        if (type.isArray()) {
            for (JsonNode t : type) {
                if (t.isTextual() && "JobPosting".equalsIgnoreCase(t.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isTextual()) {
            return null;
        }
        String s = v.asText().strip();
        return s.isEmpty() ? null : s;
    }

    private static String meta(Document doc, String property) {
        String safe = property.replace("\"", "");
        Element el = doc.selectFirst("meta[property=\"" + safe + "\"]");
        if (el == null) {
            el = doc.selectFirst("meta[name=\"" + safe + "\"]");
        }
        if (el == null) {
            return null;
        }
        String c = el.attr("content");
        return c == null || c.isBlank() ? null : c.strip();
    }

    private static String cleanRole(String title) {
        if (title == null) {
            return null;
        }
        String t = title.strip().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private static ParsedTitle parseTitleTag(String title, String hostLower) {
        String t = title.strip();
        if (t.isEmpty()) {
            return new ParsedTitle(null, null);
        }

        if (hostLower.contains("amazon.jobs")) {
            Matcher am = AMAZON_JOB_ID.matcher(t);
            if (am.find()) {
                return new ParsedTitle(cleanRole(am.group(1)), "Amazon");
            }
        }

        String[] pipes = PIPE_SPLIT.split(t);
        if (pipes.length >= 2) {
            String first = pipes[0].strip();
            String second = pipes[1].strip();
            String lastSegment = pipes[pipes.length - 1].strip().toLowerCase(Locale.ROOT);
            if (lastSegment.contains("linkedin")
                    || lastSegment.contains("indeed")
                    || lastSegment.contains("glassdoor")
                    || lastSegment.contains("amazon.jobs")
                    || lastSegment.contains("amazon jobs")) {
                String comp = lastSegment.contains("amazon") ? "Amazon" : second;
                return new ParsedTitle(first, comp);
            }
        }

        var atMatcher = AT_SPLIT.matcher(t);
        if (atMatcher.find()) {
            String role = t.substring(0, atMatcher.start()).strip();
            String comp = t.substring(atMatcher.end()).strip();
            comp = PIPE_SPLIT.splitAsStream(comp).findFirst().orElse(comp).strip();
            return new ParsedTitle(emptyToNull(role), emptyToNull(comp));
        }

        if (pipes.length >= 2) {
            return new ParsedTitle(pipes[0].strip(), pipes[1].strip());
        }

        String[] dash = t.split("\\s+-\\s+", 2);
        if (dash.length == 2 && dash[0].length() <= 80 && dash[1].length() <= 80) {
            return new ParsedTitle(dash[0].strip(), dash[1].strip());
        }

        return new ParsedTitle(t, null);
    }

    private static String emptyToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.strip();
    }

    private record Extracted(String title, String company) {}

    private record ParsedTitle(String role, String company) {}
}
