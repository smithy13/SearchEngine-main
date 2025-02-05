package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;
import searchengine.model.Status;

import java.util.List;

@Repository
public interface SiteEntityRepository extends JpaRepository<SiteEntity, Long> {

    void deleteAll();

    @Transactional
    @Modifying
    @Query(value = "CALL proc_truncate_sites", nativeQuery = true)
    void truncateSites();

    List<SiteEntity> findByUrl(String url);

    List<SiteEntity> findByStatus(Status status);
}
