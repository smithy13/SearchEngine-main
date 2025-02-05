package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class LemmaExtractionTask implements Callable<List<String>> {

    private static final Logger log = LoggerFactory.getLogger(LemmaExtractionTask.class);
    private final String pageText;
    private final LemmaProcessingService lemmaProcessingService;

    public LemmaExtractionTask(String pageText, LemmaProcessingService lemmaProcessingService) {
        this.pageText = pageText;
        this.lemmaProcessingService = lemmaProcessingService;
    }

    @Override
    public List<String> call() {
        List<String> words = Arrays.asList(pageText.split("\\s+"));

        List<String> lemmaList = new ArrayList<>(lemmaProcessingService.getLemmaRuList(lemmaProcessingService.getWordsRu(words)));
        lemmaList.addAll(lemmaProcessingService.getLemmaEnList(lemmaProcessingService.getWordsEn(words)));

        log.info("Extracted {} lemmas from the page.", lemmaList.size());
        return lemmaList;
    }
}