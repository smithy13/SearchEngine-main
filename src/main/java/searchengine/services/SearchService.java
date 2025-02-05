package searchengine.services;

import lombok.Getter;
import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.LemmaDto;
import searchengine.dto.search.Data;
import searchengine.dto.search.SearchResult;
import searchengine.model.*;
import searchengine.repository.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Getter
@Setter
public class SearchService {
    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    @Autowired
    private LemmaProcessingService lemmaProcessingService;
    @Autowired
    private SiteEntityRepository siteEntityRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private PageRepository pageRepository;

    private List<String> queryLemmas = new ArrayList<>();
    private List<String> wordsQuery = new ArrayList<>();

    public SearchResult search(String query, int offset, int limit, String site) {
        queryLemmas.clear();
        wordsQuery.clear();

        List<Data> dataList = new ArrayList<>();
        wordsQuery.addAll(parseWords(query, "ru"));
        wordsQuery.addAll(parseWords(query, "en"));

        queryLemmas.addAll(lemmaProcessingService.getLemmaRuList(wordsQuery));
        queryLemmas.addAll(lemmaProcessingService.getLemmaEnList(wordsQuery));

        if (site == null) {
            siteEntityRepository.findAll().stream()
                    .filter(s -> s.getStatus().equals(Status.INDEXED))
                    .forEach(s -> dataList.addAll(searchOnes(offset, limit, s.getUrl())));
        } else {
            dataList.addAll(searchOnes(offset, limit, site));
        }

        return new SearchResult(!dataList.isEmpty(), dataList.size(), dataList);
    }

    private List<Data> searchOnes(int offset, int limit, String site) {
        Optional<SiteEntity> siteEntityOpt = siteEntityRepository.findByUrl(site).stream().findFirst();
        if (siteEntityOpt.isEmpty()) return Collections.emptyList();

        SiteEntity siteEntity = siteEntityOpt.get();
        List<Data> dataList = new ArrayList<>();
        List<LemmaDto> queryLemmasSorted = getSortedLemmas(siteEntity.getId());

        if (!queryLemmasSorted.isEmpty()) {
            List<IndexEntity> indicesByQuery = getIndicesByQuery(queryLemmasSorted, siteEntity.getId());
            List<Page> pageList = pageRepository.findByIdIn(indicesByQuery.stream().map(IndexEntity::getPageId).toList());
            Map<Long, Float> absRelevanceList = new HashMap<>();

            for (Page page : pageList) {
                absRelevanceList.put(page.getId(), getAbsRelevance(indexRepository.findByPageId(page.getId()), queryLemmasSorted));
                Document doc = Jsoup.parse(page.getContent());
                String snippet = getSnippet(doc);
                if (!snippet.isEmpty()) {
                    dataList.add(new Data(page.getPath(), siteEntity.getUrl(), siteEntity.getName(), doc.title(), snippet));
                }
            }

            if (!absRelevanceList.isEmpty()) {
                float maxRel = Collections.max(absRelevanceList.values());
                for (Map.Entry<Long, Float> entry : absRelevanceList.entrySet()) {
                    pageRepository.findById(entry.getKey()).flatMap(page -> dataList.stream()
                            .filter(d -> d.getUri().equals(page.getPath()))
                            .findFirst()).ifPresent(d -> d.setRelevance(entry.getValue() / maxRel));
                }
            }
        }
        dataList.sort(Comparator.comparing(Data::getRelevance).reversed());
        return dataList;
    }

    private List<LemmaDto> getSortedLemmas(Long siteId) {
        return lemmaRepository.findAllBySiteEntityId(siteId).stream()
                .filter(lemma -> queryLemmas.contains(lemma.getLemma().trim()))
                .map(lemma -> new LemmaDto(lemma.getId(), lemma.getLemma(), lemma.getFrequency()))
                .sorted(Comparator.comparing(LemmaDto::getLemma))
                .toList();
    }

    private List<IndexEntity> getIndicesByQuery(List<LemmaDto> queryLemmas, long siteId) {
        List<Long> pagesIdList = queryLemmas.stream()
                .flatMap(lemma -> indexRepository.findByLemmaId(lemma.getId()).stream())
                .map(IndexEntity::getPageId)
                .distinct()
                .toList();
        return indexRepository.findByPageIdIn(pagesIdList);
    }

    private float getAbsRelevance(List<IndexEntity> indexList, List<LemmaDto> queryWords) {
        return (float) indexList.stream()
                .filter(index -> queryWords.stream().anyMatch(lemma -> lemma.getId().equals(index.getLemmaId())))
                .mapToDouble(IndexEntity::getRank)
                .sum();
    }

    private String getSnippet(Document doc) {
        List<String> pageWords = Arrays.stream(doc.body().text().split("\\s+")).toList();
        int startNdx = getStartIndex(pageWords);
        if (startNdx == pageWords.size()) return "";
        int stopNdx = Math.min(startNdx + 30, pageWords.size());
        return pageWords.subList(startNdx, stopNdx).stream()
                .map(w -> wordsQuery.contains(w) ? "<b>" + w + "</b>" : w)
                .collect(Collectors.joining(" "));
    }

    private int getStartIndex(List<String> pageWords) {
        return wordsQuery.stream().map(pageWords::indexOf).filter(i -> i >= 0).min(Integer::compareTo).orElse(pageWords.size());
    }

    private List<String> parseWords(String text, String lang) {
        return Arrays.stream(text.replaceAll(lang.equals("ru") ? "[^а-яА-Я_-]+" : "[^a-zA-Z_-]+", " ").split("\\s+"))
                .map(String::toLowerCase)
                .filter(w -> w.length() > 3)
                .toList();
    }

    public List<String> getLemmaRuList(List<String> words) {
        return new ArrayList<>();
    }

    public List<String> getLemmaEnList(List<String> words) {
        return new ArrayList<>();
    }
}