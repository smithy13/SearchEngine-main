package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;


public class LemmaExtractionTask implements Callable<List<String>> {

    private static final Logger log = LoggerFactory.getLogger(LemmaExtractionTask.class);
    String pageText;
    public LemmaExtractionTask(String pageText){
        this.pageText = pageText;
    }

    @Override
    public List<String> call() throws Exception {
        LemmaProcessingService ls = new LemmaProcessingService();

        List<String> lemmaList = new ArrayList<>(ls.getLemmaRuList(ls.getWordsRu(pageText)));
        lemmaList.addAll(ls.getLemmaEnList(ls.getWordsEn(pageText)));

       return lemmaList;
    }


}
