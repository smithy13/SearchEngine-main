package searchengine.services;

import lombok.Getter;
import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.RequestResult;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Service
@Getter
@Setter
public class SiteDataService {

    private static final String DB_NAME = "search_engine";
    private static final String DB_USER = "skillbox";
    private static final String DB_PWD = "skillbox";
    private static final String URL_PATTERN_HEAD = "^[htpsw.:/]+[a-zA-Z\\.]+\\.[a-z]+";
    private static final String URL_PATTERN_TAIL = "\\/[_\\-\\/a-z0-9]+$";
//    private static final String URL_PATTERN = "^[htps:/]+[w]{0,3}[\\.]{0,1}";

    public Connection getConnection() throws SQLException {
        String url = String.format("jdbc:mysql://localhost:3306/%s?user=%s&password=%s", DB_NAME, DB_USER, DB_PWD);
        return DriverManager.getConnection(url);
    }

    public void execSql(String sql) {
        try (Connection connection = getConnection()) {
            connection.createStatement().execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("SQL execution failed", e);
        }
    }

    public Document getDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .userAgent(getParamByKey("agent"))
                .referrer(getParamByKey("referer"))
                .timeout(Integer.parseInt(getParamByKey("timeout")))
                .get();
    }

    public int statusCode(String path) {
        try {
            Document document = getDocument(path);
            if (!document.title().isEmpty()) {
                return 200;
            } else {
                Response response = Jsoup.connect(path)
                        .userAgent(getParamByKey("agent"))
                        .timeout(Integer.parseInt(getParamByKey("timeout")))
                        .execute();
                return response.statusCode();
            }
        } catch (IOException e) {
            System.err.println("IOException occurred: " + e.getMessage());
            return 500;
        }
    }

    public ResponseEntity<?> throwException() {
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .body(new RequestResult("Эта страница находится за пределами веб-сайтов, перечисленных в файле конфигурации"));
    }

    private String getParamByKey(String key) {
        return SiteIndexerService.appParam.get(key);
    }

    public String getUrlPatternTail() {
        return URL_PATTERN_TAIL;
    }

    public String getUrlPatternHead() {
        return URL_PATTERN_HEAD;
    }
}
