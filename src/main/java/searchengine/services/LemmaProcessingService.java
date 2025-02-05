package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.RequestResult;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteEntityRepository;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LemmaProcessingService {

    private static final List<String> PARTICLE_NAMES = List.of("МЕЖД", "ПРЕДЛ", "СОЮЗ");

    private final SiteEntityRepository siteEntityRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;

    private final LuceneMorphology luceneMorphRus;
    private final LuceneMorphology luceneMorphEng;

    public List<String> getLemmaRuList(List<String> words) {
        return getLemmasFromWords(words, luceneMorphRus);
    }

    public List<String> getLemmaEnList(List<String> words) {
        return getLemmasFromWords(words, luceneMorphEng);
    }

    public List<String> getWordsRu(List<String> words) {
        return getWordsFromText(words, "[а-яА-Я]+", luceneMorphRus);
    }

    public List<String> getWordsEn(List<String> words) {
        return getWordsFromText(words, "[a-zA-Z]+", luceneMorphEng);
    }

    private List<String> getWordsFromText(List<String> words, String regex, LuceneMorphology morphology) {
        return words.stream()
                .map(word -> word.replaceAll("[^" + regex + "]", ""))
                .filter(word -> word.length() > 3)
                .collect(Collectors.toList());
    }

    public ResponseEntity<?> indexSinglePage(String path) {
        SiteDataService siteDataService = new SiteDataService();
        TreeMap<String, Integer> indexPageList = new TreeMap<>();

        String urlHead = path.replaceAll(siteDataService.getUrlPatternTail(), "");
        String urlTail = path.replaceAll(siteDataService.getUrlPatternHead(), "");

        Optional<SiteEntity> siteEntityOpt = siteEntityRepository.findByUrl(urlHead).stream().findFirst();
        if (siteEntityOpt.isEmpty()) {
            log.warn("No site found for URL: {}", urlHead);
            return ResponseEntity.ok(new RequestResult(false));
        }

        SiteEntity siteEntity = siteEntityOpt.get();
        Long siteId = siteEntity.getId();
        Optional<Page> pageOpt = pageRepository.findByPath(urlTail).stream().findFirst();
        Long pageId = pageOpt.map(Page::getId).orElse(0L);

        List<Lemma> lemmaList = lemmaRepository.findAllBySiteEntityId(siteId);

        Document doc;
        try {
            doc = siteDataService.getDocument(path);
        } catch (IOException e) {
            log.error("Error fetching document", e);
            return siteDataService.throwException();
        }

        indexRepository.deleteByPageId(pageId);
        pageRepository.deleteById(pageId);

        int statusCode = siteDataService.statusCode(path);
        Page page = new Page(urlTail, statusCode, doc.html(), siteEntity);
        pageRepository.save(page);

        try {
            log.info("Processing lemmas...");
            String pageText = Jsoup.parse(page.getContent()).text();
            indexPageList.putAll(getLemmas(pageText));
            updateLemmas(lemmaList, indexPageList, siteDataService);
        } catch (IOException e) {
            log.error("Error processing lemmas", e);
        }

        return ResponseEntity.ok(new RequestResult(true));
    }

    private TreeMap<String, Integer> getLemmas(String text) throws IOException {
        return getAllLemmas(text).stream()
                .filter(word -> word.length() > 3)
                .collect(Collectors.toMap(word -> word, word -> 1, Integer::sum, TreeMap::new));
    }

    private List<String> getAllLemmas(String text) throws IOException {
        List<String> lemmas = new ArrayList<>();
        lemmas.addAll(getLemmasFromWords(getWords(text, "[а-яА-Я]+"), luceneMorphRus));
        lemmas.addAll(getLemmasFromWords(getWords(text, "[a-zA-Z]+"), luceneMorphEng));
        return lemmas;
    }

    private List<String> getLemmasFromWords(List<String> words, LuceneMorphology morphology) {
        return words.stream()
                .filter(word -> word.length() > 3 && morphology.getMorphInfo(word).stream().noneMatch(PARTICLE_NAMES::contains))
                .flatMap(word -> {
                    try {
                        return morphology.getNormalForms(word).stream();
                    } catch (Exception e) {
                        log.warn("Error processing word: {}", word, e);
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());
    }

    private List<String> getWords(String text, String regex) {
        return Arrays.stream(text.replaceAll("[^" + regex + "]+", " ").split("\\s+"))
                .map(String::toLowerCase)
                .filter(word -> word.length() > 3)
                .collect(Collectors.toList());
    }

    private void updateLemmas(List<Lemma> lemmaList, TreeMap<String, Integer> indexPageList, SiteDataService siteDataService) {
        lemmaList.forEach(lemma -> {
            if (indexPageList.containsKey(lemma.getLemma())) {
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaRepository.save(lemma);
            }
        });

        String insertSQL = indexPageList.keySet().stream()
                .filter(integer -> lemmaList.stream().noneMatch(l -> l.getLemma().equals(integer)))
                .map(integer -> "(" + 1 + ", '" + integer + "')")
                .collect(Collectors.joining(", "));

        if (!insertSQL.isEmpty()) {
            siteDataService.execSql("INSERT INTO lemmas (frequency, lemma) VALUES " + insertSQL);
        }
    }

    public ResponseEntity<?> IndexOnePage(String path) {
        return indexSinglePage(path);
    }
}