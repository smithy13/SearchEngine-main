package searchengine.services;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import searchengine.dto.PageDto;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SitePagesRecursiveTask extends RecursiveTask<List<PageDto>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SitePagesRecursiveTask.class);

    private final Set<String> uniqueLinks;
    private final String basePath;
    private final String currentPath;
    private final Long siteID;
    private final SiteDataService siteDataService;

    public SitePagesRecursiveTask(Set<String> uniqueLinks, String basePath, String currentPath, Long siteID, SiteDataService siteDataService) {
        this.uniqueLinks = uniqueLinks;
        this.basePath = basePath;
        this.currentPath = currentPath;
        this.siteID = siteID;
        this.siteDataService = siteDataService;
    }

    @Override
    protected List<PageDto> compute() {
        List<PageDto> pagesList = new ArrayList<>();
        List<SitePagesRecursiveTask> taskList = new ArrayList<>();
        Pattern pattern = Pattern.compile("(/[a-zA-Z0-9\\-_а-я?=%]+)+[\\.html]{0,5}");

        Document doc;
        try {
            doc = siteDataService.getDocument(currentPath);
        } catch (IOException e) {
            LOGGER.warn("Ошибка при получении документа: {} | Базовый путь: {}", currentPath, basePath);
            return pagesList;
        }

        String cleanedPath = currentPath.replaceAll("^[htps:/]+[a-zA-Z\\.]+\\.[a-z]+", "").replaceAll("//$", "");
        pagesList.add(new PageDto(
                cleanedPath.isEmpty() ? "/" : cleanedPath,
                siteDataService.statusCode(currentPath),
                doc.html().replace("'", "\""),
                siteID
        ));

        for (Element element : doc.select("a")) {
            String relativePath = element.absUrl("href")
                    .replaceAll("^[htps:/w.]+[a-z]+\\.[a-z]{2,3}", "")
                    .replaceAll("/$", "");
            String absoluteUrl = element.absUrl("href")
                    .replaceAll("^[htps]+", "http")
                    .replaceAll("/$", "");

            Matcher matcher = pattern.matcher(relativePath);
            if (absoluteUrl.contains(basePath) && !relativePath.contains(basePath) && uniqueLinks.add(relativePath) && matcher.matches()) {
                processLink(pagesList, taskList, absoluteUrl, relativePath);
            }
        }

        if (!SiteIndexerService.isInterrupted) {
            for (SitePagesRecursiveTask task : taskList) {
                pagesList.addAll(task.join());
            }
        }

        return pagesList;
    }

    private void processLink(List<PageDto> pagesList, List<SitePagesRecursiveTask> taskList, String absoluteUrl, String relativePath) {
        String content = "";
        int statusCode = 200;

        try {
            Document document = siteDataService.getDocument(absoluteUrl);
            content = document.html().replace("'", "\"");

            if (content.isEmpty()) {
                statusCode = siteDataService.statusCode(absoluteUrl);
            }

            pagesList.add(new PageDto(relativePath, statusCode, content, siteID));
        } catch (IOException e) {
            LOGGER.warn("Ошибка при обработке ссылки: {}", absoluteUrl);
        }

        if (statusCode < 300 && !content.isEmpty() && !SiteIndexerService.isInterrupted) {
            SitePagesRecursiveTask task = new SitePagesRecursiveTask(uniqueLinks, basePath, absoluteUrl, siteID, siteDataService);
            task.fork();
            taskList.add(task);
        }
    }
}
