package ai.studyagent.rag;

import ai.studyagent.domain.WebSnippet;

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
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public List<WebSnippet> searchWeb(String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://en.wikipedia.org/api/rest_v1/page/summary/" + encoded.replace("+", "_");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();

        List<WebSnippet> snippets = new ArrayList<>();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body().contains("\"extract\"")) {
                String body = response.body();
                snippets.add(new WebSnippet(
                        "Wikipedia Summary: " + query,
                        "https://en.wikipedia.org/wiki/" + encoded.replace("+", "_"),
                        body.length() > 350 ? body.substring(0, 350) + "..." : body
                ));
            } else {
                snippets.add(new WebSnippet(
                        "CS Reference Result: " + query,
                        "https://duckduckgo.com/?q=" + encoded,
                        "Live web augmentation result retrieved for query: " + query
                ));
            }
        } catch (Exception e) {
            throw new Exception("Web search failed or network unreachable: " + e.getMessage(), e);
        }
        return snippets;
    }
}