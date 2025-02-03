package searchengine.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestResult {
    private boolean result;
    private String error;

    public RequestResult(boolean result) {
        this.result = result;
    }

    public RequestResult(String error) {
        this.result = false;
        this.error = error;
    }
}
