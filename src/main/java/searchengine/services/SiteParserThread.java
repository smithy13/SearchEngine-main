package searchengine.services;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import searchengine.dto.IndexPage;
import searchengine.model.SiteEntity;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Status;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteEntityRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

public class SiteParserThread implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(SiteParserThread.class);

    private final LemmaProcessingService lemmaProcessingService;

    private final String pathWithoutWWW;
    private final String pagePath;
    private final PageRepository pageRepository;
    private final SiteEntityRepository siteEntityRepository;
    private final LemmaRepository lemmaRepository;
    private final StringBuilder insertQuery = new StringBuilder();

    public SiteParserThread(String pathWithoutWWW, String pagePath,
                            PageRepository pageRepository,
                            SiteEntityRepository siteEntityRepository,
                            LemmaRepository lemmaRepository,
                            LemmaProcessingService lemmaProcessingService) {
        this.pathWithoutWWW = pathWithoutWWW;
        this.pagePath = pagePath;
        this.pageRepository = pageRepository;
        this.siteEntityRepository = siteEntityRepository;
        this.lemmaRepository = lemmaRepository;
        this.lemmaProcessingService = lemmaProcessingService;
    }

    @Override
    public void run() {
        long startMillis = System.currentTimeMillis();
        log.info("Начало парсинга сайта: {}", pathWithoutWWW);

        SiteEntity site = SiteIndexerService.sitesEntityMap.get(pathWithoutWWW);
        long siteId = site.getId();

        log.info("siteURL: {}", pathWithoutWWW);
        log.info("pagePath: {}", pagePath);
        log.info("siteId: {}", siteId);

        ForkJoinPool forkJoinPool = new ForkJoinPool();
        try {
            List<Page> pages = pageRepository.findBySiteEntity(site);
            List<Page> pagesNoError = pages.stream().filter(p -> p.getCode() < 300).toList();

            ExecutorService service = Executors.newFixedThreadPool(2);
            try {
                Map<String, Future<List<String>>> taskMap = new HashMap<>();
                Map<String, List<String>> pagesLemmasMap = new HashMap<>();

                for (Page p : pagesNoError) {
                    String pageText = Jsoup.parse(p.getContent()).body().text();
                    Future<List<String>> futureTask = service.submit(new LemmaExtractionTask(pageText, lemmaProcessingService));
                    taskMap.put(p.getPath(), futureTask);
                }

                for (Map.Entry<String, Future<List<String>>> entry : taskMap.entrySet()) {
                    try {
                        pagesLemmasMap.put(entry.getKey(), entry.getValue().get());
                    } catch (InterruptedException | ExecutionException e) {
                        log.error("Ошибка при извлечении лемм", e);
                        Thread.currentThread().interrupt();
                    }
                }

                List<Lemma> lemmasDb = lemmaRepository.findAllBySiteEntityId(siteId);
                Map<String, Long> lemmaIdMap = new HashMap<>();
                for (Lemma lemma : lemmasDb) {
                    lemmaIdMap.put(lemma.getLemma(), lemma.getId());
                }

                List<IndexPage> indexPageList = new ArrayList<>();
                for (Page page : pagesNoError) {
                    List<String> lemmasOnPage = pagesLemmasMap.getOrDefault(page.getPath(), Collections.emptyList());
                    Set<String> uniqueLemmas = new HashSet<>();

                    for (String lemma : lemmasOnPage) {
                        long rank = lemmasOnPage.stream().filter(l -> l.equals(lemma)).count();
                        if (rank > 0 && uniqueLemmas.add(lemma + "_" + page.getId())) {
                            Long lemmaId = Optional.ofNullable(lemmaIdMap.get(lemma))
                                    .orElseThrow(() -> new IllegalStateException("Лемма не найдена в БД"));
                            indexPageList.add(new IndexPage(page.getId(), lemmaId, rank));
                        }
                    }
                }
            } finally {
                service.shutdown();
                try {
                    if (!service.awaitTermination(60, TimeUnit.SECONDS)) {
                        service.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    service.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            site.setStatus(Status.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            siteEntityRepository.save(site);

            log.info("==== Парсинг завершен: {} за {} мс", pathWithoutWWW, (System.currentTimeMillis() - startMillis));
        } catch (Exception e) {
            log.error("Ошибка при парсинге сайта: {}", pathWithoutWWW, e);
            site.setStatus(Status.FAILED);
            site.setLastError("Ошибка при парсинге: " + e.getMessage());
            site.setStatusTime(LocalDateTime.now());
            siteEntityRepository.save(site);
        } finally {
            forkJoinPool.shutdown();
            try {
                if (!forkJoinPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    forkJoinPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                forkJoinPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
