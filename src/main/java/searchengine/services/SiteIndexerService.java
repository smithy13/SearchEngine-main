package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
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

@Service
public class SiteIndexerService {

    @Autowired
    private SiteList siteList;

    @Autowired
    private ParameterList parameterList;

    @Autowired
    private SiteEntityRepository siteEntityRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private LemmaProcessingService lemmaProcessingService;

    private static final String URL_PATTERN = "^[htps:/]+[w]{0,3}[\\.]{0,1}";
    static final TreeMap<String, SiteEntity> sitesEntityMap = new TreeMap<>();
    static final TreeMap<String, String> appParam = new TreeMap<>();
    static volatile boolean isInterrupted = false;
    private static final Logger log = LoggerFactory.getLogger(SiteIndexerService.class);

    public ResponseEntity<?> runIndexing() {
        try {
            createAppParam();

            indexRepository.truncateIndices();
            lemmaRepository.truncateLemmas();
            pageRepository.truncatePages();
            siteEntityRepository.truncateSites();

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
                            pathWithoutWWW, site.getUrl(), pageRepository, siteEntityRepository, lemmaRepository
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
