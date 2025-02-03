package searchengine.dto.search;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Setter
@Getter
public class SearchResult {
    private boolean result;
    private int count = 0;
    private List<Data> data;

    public SearchResult(boolean result, int count, List<Data> data) {
        this.result = result;
        this.count = count;
        this.data = data;
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "result=" + result +
                ", count=" + count +
                ", data=" + data +
                '}';
    }
}
