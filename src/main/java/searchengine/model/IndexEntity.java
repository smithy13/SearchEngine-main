package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "indices")
public class IndexEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private Long pageId;

    @Column(name = "lemma_id", nullable = false)
    private Long lemmaId;

    @Column(name = "rank", nullable = false)
    private Float rank;

    public IndexEntity() {}

    public IndexEntity(Long pageId, Long lemmaId, Float rank) {
        this.pageId = pageId;
        this.lemmaId = lemmaId;
        this.rank = rank;
    }
}
