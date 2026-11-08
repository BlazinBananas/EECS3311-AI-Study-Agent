package ai.studyagent.rag;

import ai.studyagent.domain.WebSnippet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class WebSearchTool {
    private final HttpClient httpClient;

    public WebSearchTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<WebSnippet> searchWeb(String query) throws Exception {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query cannot be empty.");
        }

        List<WebSnippet> snippets = new ArrayList<>();
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);

        // 1. Live Wikipedia Full-Text Search API (returns real article snippets for any question)
        String wikiSearchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch="
                + encoded + "&utf8=1&format=json&srlimit=3";

        HttpRequest wikiReq = HttpRequest.newBuilder()
                .uri(URI.create(wikiSearchUrl))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "EECS3311-StudyAgent/1.0 (Academic Course Project)")
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> wikiResp = httpClient.send(wikiReq, HttpResponse.BodyHandlers.ofString());
        if (wikiResp.statusCode() == 200) {
            JsonObject root = JsonParser.parseString(wikiResp.body()).getAsJsonObject();
            if (root.has("query") && root.getAsJsonObject("query").has("search")) {
                JsonArray results = root.getAsJsonObject("query").getAsJsonArray("search");
                for (JsonElement elem : results) {
                    JsonObject item = elem.getAsJsonObject();
                    String title = item.get("title").getAsString();
                    String rawSnippet = item.get("snippet").getAsString();
                    String cleanSnippet = stripHtmlTags(rawSnippet);
                    String articleUrl = "https://en.wikipedia.org/wiki/"
                            + URLEncoder.encode(title.replace(" ", "_"), StandardCharsets.UTF_8);
                    snippets.add(new WebSnippet(title, articleUrl, cleanSnippet));
                }
            }
        }

        // 2. Secondary Check: DuckDuckGo Instant Answer API if Wikipedia returned nothing
        if (snippets.isEmpty()) {
            String ddgUrl = "https://api.duckduckgo.com/?q=" + encoded + "&format=json&no_html=1&skip_disambig=1";
            HttpRequest ddgReq = HttpRequest.newBuilder()
                    .uri(URI.create(ddgUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "EECS3311-StudyAgent/1.0")
                    .GET()
                    .build();
            HttpResponse<String> ddgResp = httpClient.send(ddgReq, HttpResponse.BodyHandlers.ofString());
            if (ddgResp.statusCode() == 200) {
                JsonObject ddgRoot = JsonParser.parseString(ddgResp.body()).getAsJsonObject();
                if (ddgRoot.has("AbstractText") && !ddgRoot.get("AbstractText").getAsString().isBlank()) {
                    snippets.add(new WebSnippet(
                            ddgRoot.get("Heading").getAsString(),
                            ddgRoot.get("AbstractURL").getAsString(),
                            ddgRoot.get("AbstractText").getAsString()
                    ));
                }
            }
        }

        if (snippets.isEmpty()) {
            throw new IllegalStateException("No live web snippets matched query: " + query);
        }
        return snippets;
    }

    private String stripHtmlTags(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "")
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&amp;", "&")
                .trim();
    }
}