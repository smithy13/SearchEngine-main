package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;
import searchengine.model.Page;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Long> {

    @Transactional
    @Modifying
    void deleteByPage(Page page);

    @Transactional
    @Modifying
    @Query(value = "TRUNCATE TABLE indices", nativeQuery = true)
    void truncateIndices();

    List<IndexEntity> findByPage(Page page);

    List<IndexEntity> findByLemmaId(Long lemma);

    List<IndexEntity> findByPageIn(List<Page> pages);
}
