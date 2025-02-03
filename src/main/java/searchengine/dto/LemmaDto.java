package searchengine.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LemmaDto implements Comparable<LemmaDto> {
    private Long id;
    private String lemma;
    private Integer frequency;
    private Long siteId;

    @Override
    public int compareTo(LemmaDto o) {
        return Integer.compare(this.frequency, o.getFrequency());
    }

    @Override
    public String toString() {
        return "Lemma : " + getLemma() + "\n" +
                "SiteId : " + siteId;
    }
}
