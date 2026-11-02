package ai.studyagent.domain;

public class WebSnippet {
    private String title;
    private String url;
    private String summaryText;

    public WebSnippet(String title, String url, String summaryText) {
        this.title = title;
        this.url = url;
        this.summaryText = summaryText;
    }

    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getSummaryText() { return summaryText; }

    @Override
    public String toString() {
        return title + " (" + url + "): " + summaryText;
    }
}