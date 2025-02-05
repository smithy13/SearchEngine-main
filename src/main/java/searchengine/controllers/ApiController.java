package searchengine.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchResult;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.SearchService;
import searchengine.services.SiteIndexerService;
import searchengine.services.StatisticsServiceImpl;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);
    private static final int DEFAULT_OFFSET = 2;
    private static final int DEFAULT_LIMIT = 8;

    private final StatisticsServiceImpl statisticsService;
    private final SearchService searchService;
    private final SiteIndexerService siteIndexerService;

    public ApiController(StatisticsServiceImpl statisticsService,
                         SearchService searchService,
                         SiteIndexerService siteIndexerService) {
        this.statisticsService = statisticsService;
        this.searchService = searchService;
        this.siteIndexerService = siteIndexerService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        log.info("Request for statistics received");
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<String> startIndexing() {
        log.info("Indexing started");
        try {
            siteIndexerService.runIndexing();
            return ResponseEntity.ok("Indexing started successfully.");
        } catch (Exception e) {
            log.error("Indexing failed", e);
            return ResponseEntity.status(500).body("Indexing failed: " + e.getMessage());
        }
    }

    @PostMapping("/indexPage")
    public ResponseEntity<String> indexPage(@RequestParam String url) throws IOException {
        log.info("Indexing page: {}", url);
        siteIndexerService.indexPage(url);
        return ResponseEntity.ok("Page indexed successfully.");
    }

    @GetMapping("/search")
    public SearchResult search(@RequestParam String query,
                               @RequestParam(defaultValue = "2") int offset,
                               @RequestParam(defaultValue = "8") int limit,
                               @RequestParam(value = "site", required = false) String site) throws IOException {
        log.info("Search request received for query: {}", query);
        return searchService.search(query, offset, limit, site);
    }
}
