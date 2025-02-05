package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Long> {

    @Transactional
    @Modifying
    void deleteByPageId(Long pageId);

    @Transactional
    @Modifying
    @Query(value = "TRUNCATE TABLE indices", nativeQuery = true)
    void truncateIndices();

    List<IndexEntity> findByPageId(Long pageId);

    List<IndexEntity> findByLemmaId(Long lemmaId);

    List<IndexEntity> findByPageIdIn(List<Long> ids);
}
