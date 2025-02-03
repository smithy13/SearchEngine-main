package searchengine.services;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import searchengine.dto.IndexPage;
import searchengine.dto.PageDto;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteEntityRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

public class SiteParserThread implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(SiteParserThread.class);

    private final String pathWithoutWWW;
    private final String pagePath;
    private final PageRepository pageRepository;
    private final SiteEntityRepository siteEntityRepository;
    private final LemmaRepository lemmaRepository;
    private final StringBuilder insertQuery = new StringBuilder();

    public SiteParserThread(String pathWithoutWWW, String pagePath,
                            PageRepository pageRepository,
                            SiteEntityRepository siteEntityRepository,
                            LemmaRepository lemmaRepository) {
        this.pathWithoutWWW = pathWithoutWWW;
        this.pagePath = pagePath;
        this.pageRepository = pageRepository;
        this.siteEntityRepository = siteEntityRepository;
        this.lemmaRepository = lemmaRepository;
    }

    @Override
    public void run() {
        long startMillis = System.currentTimeMillis();
        log.info("Начало парсинга сайта: {}", pathWithoutWWW);

        LinkChecker linkChecker = new LinkChecker();
        SiteDataService siteDataService = new SiteDataService();

        SiteEntity site = SiteIndexerService.sitesEntityMap.get(pathWithoutWWW);
        long siteId = site.getId();

        log.info("siteURL: {}", pathWithoutWWW);
        log.info("pagePath: {}", pagePath);
        log.info("siteId: {}", siteId);

        TreeSet<PageDto> pagesList = new TreeSet<>(new ForkJoinPool().invoke(
                new SitePagesRecursiveTask(linkChecker.getUniqueLinks(), pathWithoutWWW, pagePath, siteId)
        ));

        log.info("==== Вставка в таблицу `pages` завершена. Найдено страниц: {}", pagesList.size());

        try {
            for (PageDto page : pagesList) {
                insertQuery.append(insertQuery.isEmpty() ? "" : ",")
                        .append("('")
                        .append(page.getCode()).append("','")
                        .append(page.getContent()).append("','")
                        .append(page.getPath()).append("',")
                        .append(siteId)
                        .append(")");

                if (insertQuery.length() >= 1_000_000) {
                    siteDataService.execSql("INSERT INTO pages (code, content, path, site_entity_id) VALUES " + insertQuery);
                    insertQuery.setLength(0);
                }
            }
            if (!insertQuery.isEmpty()) {
                siteDataService.execSql("INSERT INTO pages (code, content, path, site_entity_id) VALUES " + insertQuery);
            }
        } catch (Exception e) {
            log.error("Ошибка при вставке страниц в БД", e);
        }

        List<Page> pages = pageRepository.findAllBySiteId(siteId);
        List<Page> pagesNoError = pages.stream().filter(p -> p.getCode() < 300).toList();

        ExecutorService service = Executors.newFixedThreadPool(2);
        Map<String, Future<List<String>>> taskMap = new HashMap<>();
        Map<String, List<String>> pagesLemmasMap = new HashMap<>();

        for (Page p : pagesNoError) {
            String pageText = Jsoup.parse(p.getContent()).body().text();
            Future<List<String>> futureTask = service.submit(new LemmaExtractionTask(pageText));
            taskMap.put(p.getPath(), futureTask);
        }

        for (Map.Entry<String, Future<List<String>>> entry : taskMap.entrySet()) {
            try {
                pagesLemmasMap.put(entry.getKey(), entry.getValue().get());
            } catch (InterruptedException | ExecutionException e) {
                log.error("Ошибка при извлечении лемм", e);
            }
        }
        service.shutdown();

        insertQuery.setLength(0);

        Map<String, Integer> lemmaFrequency = new HashMap<>();
        for (List<String> lemmas : pagesLemmasMap.values()) {
            for (String lemma : new HashSet<>(lemmas)) {
                lemmaFrequency.merge(lemma, 1, Integer::sum);
            }
        }

        for (Map.Entry<String, Integer> entry : lemmaFrequency.entrySet()) {
            insertQuery.append(insertQuery.isEmpty() ? "" : ",")
                    .append("('")
                    .append(entry.getKey()).append("',")
                    .append(entry.getValue()).append(",")
                    .append(siteId)
                    .append(")");

            if (insertQuery.length() >= 1_000_000) {
                siteDataService.execSql("INSERT INTO lemmas (lemma, frequency, site_entity_id) VALUES " + insertQuery);
                insertQuery.setLength(0);
            }
        }

        if (!insertQuery.isEmpty()) {
            siteDataService.execSql("INSERT INTO lemmas (lemma, frequency, site_entity_id) VALUES " + insertQuery);
        }
        log.info("==== Вставка в таблицу `lemmas` завершена");

        List<IndexPage> indexPageList = new ArrayList<>();
        List<Lemma> lemmasDb = lemmaRepository.findAllBySiteEntityId(siteId);
        Map<String, Long> lemmaIdMap = new HashMap<>();
        for (Lemma lemma : lemmasDb) {
            lemmaIdMap.put(lemma.getLemma(), lemma.getId());
        }

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

        insertQuery.setLength(0);
        for (IndexPage item : indexPageList) {
            insertQuery.append(insertQuery.isEmpty() ? "" : ",")
                    .append("('")
                    .append(item.getLemmaId()).append("',")
                    .append(item.getPageId()).append(",")
                    .append(item.getRank())
                    .append(")");

            if (insertQuery.length() >= 1_000_000) {
                siteDataService.execSql("INSERT INTO indices (lemma_id, page_id, rank) VALUES " + insertQuery);
                insertQuery.setLength(0);
            }
        }

        if (!insertQuery.isEmpty()) {
            siteDataService.execSql("INSERT INTO indices (lemma_id, page_id, rank) VALUES " + insertQuery);
        }

        site.setStatus(Status.INDEXED);
        site.setStatusTime(LocalDateTime.now());
        siteEntityRepository.save(site);

        log.info("==== Парсинг завершен: {} за {} мс", pathWithoutWWW, (System.currentTimeMillis() - startMillis));
    }
}
