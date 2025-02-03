package searchengine.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class IndexPage implements Comparable<IndexPage> {
    private String pageUrl;
    private final Long pageId;
    private final Long lemmaId;
    private final Long rank;

    @Override
    public int compareTo(IndexPage other) {
        return this.pageUrl.compareTo(other.pageUrl);
    }
}
