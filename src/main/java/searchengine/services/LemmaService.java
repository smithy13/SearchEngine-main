package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dto.LemmaDto;
import searchengine.model.Lemma;
import searchengine.model.SiteEntity;
import searchengine.repository.LemmaRepository;
import searchengine.repository.SiteEntityRepository;

import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LemmaService implements CrudService<LemmaDto> {

    private final LemmaRepository lemmaRepository;
    private final SiteEntityRepository siteRepository;

    @Override
    public LemmaDto getById(Long id) {
        Lemma lemma = lemmaRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Lemma not found with id: {}", id);
                    return new IllegalArgumentException("Lemma not found with id: " + id);
                });
        return mapToDto(lemma);
    }

    public Collection<LemmaDto> getBySiteId(Long siteId) {
        List<Lemma> lemmaList = lemmaRepository.findAllBySiteEntityId(siteId);
        return lemmaList.stream().map(this::mapToDto).toList();
    }

    @Override
    public Collection<LemmaDto> getAll() {
        return lemmaRepository.findAll().stream().map(this::mapToDto).toList();
    }

    @Override
    public void create(LemmaDto item) {
        log.info("Creating new lemma: {}", item);
        lemmaRepository.save(mapToEntity(item));
    }

    @Override
    public void updateById(LemmaDto item) {
        if (!lemmaRepository.existsById(item.getId())) {
            log.warn("Cannot update. Lemma not found with id: {}", item.getId());
            throw new IllegalArgumentException("Cannot update. Lemma not found with id: " + item.getId());
        }
        log.info("Updating lemma with id: {}", item.getId());
        lemmaRepository.save(mapToEntity(item));
    }

    @Override
    public void deleteById(Long id) {
        if (!lemmaRepository.existsById(id)) {
            log.warn("Cannot delete. Lemma not found with id: {}", id);
            throw new IllegalArgumentException("Cannot delete. Lemma not found with id: " + id);
        }
        log.info("Deleting lemma with id: {}", id);
        lemmaRepository.deleteById(id);
    }

    private LemmaDto mapToDto(Lemma lemma) {
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

    private Lemma mapToEntity(LemmaDto lemmaDto) {
        SiteEntity siteEntity = siteRepository.findById(lemmaDto.getSiteId())
                .orElseThrow(() -> {
                    log.warn("SiteEntity not found with id: {}", lemmaDto.getSiteId());
                    return new IllegalArgumentException("SiteEntity not found with id: " + lemmaDto.getSiteId());
                });

        Lemma lemma = new Lemma(lemmaDto.getLemma(), lemmaDto.getFrequency(), siteEntity);
        lemma.setId(lemmaDto.getId());
        log.info("Mapped LemmaDto to Lemma: {}", lemma);
        return lemma;
    }
}
