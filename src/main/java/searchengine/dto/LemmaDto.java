package searchengine.dto;

import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import searchengine.model.Lemma;

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

    private static final Logger log = LoggerFactory.getLogger(LemmaDto.class);

    public static LemmaDto mapToDto(Lemma lemma) {
        if (lemma.getSiteEntity() == null) {
            log.warn("Lemma {} has no associated SiteEntity!", lemma.getId());
            throw new IllegalStateException("Lemma has no associated SiteEntity");
        }

        LemmaDto lemmaDto = new LemmaDto(
                lemma.getId(),
                lemma.getLemma(),
                lemma.getFrequency()
        );

        lemmaDto.setSiteId(lemma.getSiteEntity().getId());
        return lemmaDto;
    }
}
