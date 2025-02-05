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

    public LemmaDto(Long id, String lemma, Integer frequency) {
        this.id = id;
        this.lemma = lemma;
        this.frequency = frequency;
    }

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