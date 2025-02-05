package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {

    List<Page> findByIdIn(List<Long> ids);

    @Modifying
    @Transactional
    void deleteAll();

    @Modifying
    @Transactional
    void deleteAllInBatch();

    List<Page> findByPath(String path);

    List<Page> findBySiteEntity(SiteEntity siteEntity);
}