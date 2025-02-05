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
import java.util.concurrent.ConcurrentHashMap;
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
        ConcurrentHashMap<String, Integer> indexPageList = new ConcurrentHashMap<>();

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

        if (pageOpt.isEmpty()) {
            log.warn("No page found for path: {}", urlTail);
            return ResponseEntity.ok(new RequestResult(false));
        }

        Page page = pageOpt.get();
        List<Lemma> lemmaList = lemmaRepository.findAllBySiteEntityId(siteId);

        Document doc;
        try {
            doc = siteDataService.getDocument(path);
        } catch (IOException e) {
            log.error("Error fetching document", e);
            return siteDataService.throwException();
        }

        indexRepository.deleteByPage(page);
        pageRepository.deleteById(page.getId());

        int statusCode = siteDataService.statusCode(path);
        Page newPage = new Page(urlTail, statusCode, doc.html(), siteEntity);
        pageRepository.save(newPage);

        try {
            log.info("Processing lemmas...");
            String pageText = Jsoup.parse(newPage.getContent()).text();
            indexPageList.putAll(getLemmas(pageText));
            updateLemmas(lemmaList, indexPageList, siteEntity);
        } catch (IOException e) {
            log.error("Error processing lemmas", e);
        }

        return ResponseEntity.ok(new RequestResult(true));
    }

    private ConcurrentHashMap<String, Integer> getLemmas(String text) throws IOException {
        return getAllLemmas(text).stream()
                .filter(word -> word.length() > 3)
                .collect(Collectors.toMap(word -> word, word -> 1, Integer::sum, ConcurrentHashMap::new));
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

    private void updateLemmas(List<Lemma> lemmaList, ConcurrentHashMap<String, Integer> indexPageList, SiteEntity siteEntity) {
        lemmaList.forEach(lemma -> {
            if (indexPageList.containsKey(lemma.getLemma())) {
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaRepository.save(lemma);
            }
        });

        indexPageList.keySet().stream()
                .filter(lemmaText -> lemmaList.stream().noneMatch(l -> l.getLemma().equals(lemmaText)))
                .forEach(lemmaText -> {
                    Lemma newLemma = new Lemma(lemmaText, 1, siteEntity);
                    lemmaRepository.save(newLemma);
                });
    }

    public ResponseEntity<?> IndexOnePage(String path) {
        return indexSinglePage(path);
    }
}
