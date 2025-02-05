package searchengine.services;

import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import searchengine.config.*;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SiteIndexerService {

    private final SiteList siteList;
    private final ParameterList parameterList;
    private final SiteEntityRepository siteEntityRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaProcessingService lemmaProcessingService;

    private static final String URL_PATTERN = "^[htps:/]+[w]{0,3}[\\.]{0,1}";

    static final ConcurrentHashMap<String, SiteEntity> sitesEntityMap = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<String, String> appParam = new ConcurrentHashMap<>();

    static final AtomicBoolean isInterrupted = new AtomicBoolean(false);
    private static final Logger log = LoggerFactory.getLogger(SiteIndexerService.class);

    public SiteIndexerService(SiteList siteList,
                              ParameterList parameterList,
                              SiteEntityRepository siteEntityRepository,
                              PageRepository pageRepository,
                              LemmaRepository lemmaRepository,
                              IndexRepository indexRepository,
                              LemmaProcessingService lemmaProcessingService) {
        this.siteList = siteList;
        this.parameterList = parameterList;
        this.siteEntityRepository = siteEntityRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.lemmaProcessingService = lemmaProcessingService;
    }

    public ResponseEntity<?> runIndexing() {
        try {
            createAppParam();

            indexRepository.deleteAllInBatch();
            lemmaRepository.deleteAllInBatch();
            pageRepository.deleteAllInBatch();
            siteEntityRepository.deleteAllInBatch();

            for (Site site : siteList.getSites()) {
                SiteEntity siteEnt = new SiteEntity();
                siteEnt.setName(site.getName());
                siteEnt.setUrl(site.getUrl());
                siteEnt.setStatus(Status.INDEXING);
                siteEnt.setLastError("none");
                siteEnt.setStatusTime(LocalDateTime.now());
                siteEntityRepository.save(siteEnt);
                siteEntityRepository.flush();
            }

            for (SiteEntity siteEntity : siteEntityRepository.findAll()) {
                String baseUrlTemplate = siteEntity.getUrl().replaceAll(URL_PATTERN, "");
                sitesEntityMap.put(baseUrlTemplate, siteEntity);
            }

            ThreadPoolExecutor service = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
            service.setKeepAliveTime(1, TimeUnit.HOURS);
            service.setMaximumPoolSize(siteList.getSites().size());

            for (Site site : siteList.getSites()) {
                if (site.getUrl().contains("sendel.ru") || site.getUrl().contains("svetlovka.ru")) {
                    String pathWithoutWWW = site.getUrl().replaceAll(URL_PATTERN, "");

                    SiteParserThread siteParserThread = new SiteParserThread(
                            pathWithoutWWW, site.getUrl(), pageRepository, siteEntityRepository, lemmaRepository, lemmaProcessingService
                    );

                    service.submit(siteParserThread);
                    log.info("Запущен поток для сайта: {}", site.getUrl());
                }
            }

        } catch (Exception e) {
            log.error("Ошибка при индексации", e);
            return ResponseEntity.status(500).body(new RequestResult("Не удалось выполнить индексацию: " + e.getMessage()));
        }

        return ResponseEntity.ok(new RequestResult("Успешно выполнено"));
    }

    public ResponseEntity<?> stopIndexing() {
        try {
            isInterrupted.set(true);

            ThreadPoolExecutor service = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
            service.shutdownNow();

            log.info("Индексация была остановлена.");

            return ResponseEntity.ok(new RequestResult("Индексация успешно остановлена."));
        } catch (Exception e) {
            log.error("Ошибка при остановке индексации", e);
            return ResponseEntity.status(500).body(new RequestResult("Не удалось остановить индексацию: " + e.getMessage()));
        }
    }

    public ResponseEntity<?> indexPage(String path) {
        createAppParam();
        return lemmaProcessingService.IndexOnePage(path);
    }

    private void createAppParam() {
        List<Parameter> listParams = new ArrayList<>(parameterList.getParameters());
        for (Parameter parameter : listParams) {
            appParam.put(parameter.getName(), parameter.getValue());
        }
    }
}
