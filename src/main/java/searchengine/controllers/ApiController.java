package searchengine.controllers;

import org.jboss.logging.Logger;
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

    private static final Logger log = (Logger) LoggerFactory.getLogger(ApiController.class);
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
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<?> startIndexing() {
        return siteIndexerService.runIndexing();
    }

    @PostMapping("/indexPage")
    public ResponseEntity<?> indexPage(@RequestParam String url) throws IOException {
        return siteIndexerService.indexPage(url);
    }

    @GetMapping("/search")
    public SearchResult search(@RequestParam String query,
                               @RequestParam int offset,
                               @RequestParam int limit,
                               @RequestParam(value = "site", required = false) String site) throws IOException {
        return searchService.search(query, DEFAULT_OFFSET, DEFAULT_LIMIT, site);
    }
}
