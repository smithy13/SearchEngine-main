package searchengine.dto.search;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Data implements Comparable<Data> {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;

    @Override
    public int compareTo(Data o) {
        return Float.compare(o.relevance, this.relevance);
    }

    @Override
    public String toString() {
        return String.format("Data{site='%s', siteName='%s', uri='%s', title='%s', snippet='%s', relevance=%f}",
                site, siteName, uri, title, snippet, relevance);
    }

    public Data(String site, String siteName, String uri, String title, String snippet, float relevance) {
        this.site = site;
        this.siteName = siteName;
        this.uri = uri;
        this.title = title;
        this.snippet = snippet;
        this.relevance = relevance;
    }

    public Data(String site, String siteName, String uri, String title, String snippet) {
        this.site = site;
        this.siteName = siteName;
        this.uri = uri;
        this.title = title;
        this.snippet = snippet;
        this.relevance = 0.0f;
    }
}
