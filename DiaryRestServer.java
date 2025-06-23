import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 日記記録REST APIサーバー
 * 使用方法: java DiaryRestServer <テキストファイルパス> [ポート番号]
 * 例: java DiaryRestServer diary.txt 8080
 */
public class DiaryRestServer {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private String filePath;
    
    public DiaryRestServer(String filePath) {
        this.filePath = filePath;
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("使用方法: java DiaryRestServer <テキストファイルパス> [ポート番号]");
            System.exit(1);
        }
        
        String filePath = args[0];
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        
        try {
            DiaryRestServer server = new DiaryRestServer(filePath);
            server.start(port);
        } catch (Exception e) {
            System.err.println("サーバー起動エラー: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/diary", new DiaryHandler());
        server.setExecutor(null);
        server.start();
        
        // システム日付と時刻を表示
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss");
        
        System.out.println("=== 日記APIサーバー ===");
        System.out.println("起動日時: " + now.format(dtFormatter));
        System.out.println("ポート: " + port);
        System.out.println("データファイル: " + filePath);
        System.out.println("終了するにはCtrl+Cを押してください。");
    }
    
    private class DiaryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String query = exchange.getRequestURI().getQuery();
            
            try {
                switch (method) {
                    case "POST":
                        handlePost(exchange);
                        break;
                    case "GET":
                        handleGet(exchange, query);
                        break;
                    default:
                        sendResponse(exchange, 405, "Method Not Allowed");
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }
        
        private void handlePost(HttpExchange exchange) throws IOException {
            // リクエストボディを読み取り
            String body = readRequestBody(exchange);
            Map<String, String> params = parseFormData(body);
            
            String date = params.get("date");
            String text = params.get("text");
            
            if (date == null || text == null) {
                sendResponse(exchange, 400, "Bad Request: dateとtextパラメータが必要です");
                return;
            }
            
            // 日付フォーマットの検証
            if (!isValidDate(date)) {
                sendResponse(exchange, 400, "Bad Request: 日付はYYYYMMDD形式で入力してください");
                return;
            }
            
            // ファイルに追記
            appendToFile(date, text);
            
            // 記録内容を標準出力に出力
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            System.out.println("[" + now.format(dtFormatter) + "] 記録追加 - 日付: " + date + ", 内容: " + text);
            
            sendResponse(exchange, 200, "記録が完了しました");
        }
        
        private void handleGet(HttpExchange exchange, String query) throws IOException {
            Map<String, String> params = parseQuery(query);
            
            String date = params.get("date");
            String from = params.get("from");
            String to = params.get("to");
            
            List<String> entries;
            
            if (date != null) {
                // 特定日付の記録を取得
                if (!isValidDate(date)) {
                    sendResponse(exchange, 400, "Bad Request: 日付はYYYYMMDD形式で入力してください");
                    return;
                }
                entries = getEntriesByDate(date);
            } else if (from != null && to != null) {
                // 日付範囲の記録を取得
                if (!isValidDate(from) || !isValidDate(to)) {
                    sendResponse(exchange, 400, "Bad Request: 日付はYYYYMMDD形式で入力してください");
                    return;
                }
                entries = getEntriesByDateRange(from, to);
            } else {
                // 全件取得
                entries = getAllEntries();
            }
            
            String response = formatEntries(entries);
            sendResponse(exchange, 200, response);
        }
        
        private String readRequestBody(HttpExchange exchange) throws IOException {
            InputStream is = exchange.getRequestBody();
            return new String(is.readAllBytes(), "UTF-8");
        }
        
        private Map<String, String> parseFormData(String body) {
            Map<String, String> params = new HashMap<>();
            if (body != null && !body.isEmpty()) {
                String[] pairs = body.split("&");
                for (String pair : pairs) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        try {
                            params.put(
                                java.net.URLDecoder.decode(kv[0], "UTF-8"),
                                java.net.URLDecoder.decode(kv[1], "UTF-8")
                            );
                        } catch (Exception e) {
                            // URLデコードエラーは無視
                        }
                    }
                }
            }
            return params;
        }
        
        private Map<String, String> parseQuery(String query) {
            Map<String, String> params = new HashMap<>();
            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        try {
                            params.put(
                                java.net.URLDecoder.decode(kv[0], "UTF-8"),
                                java.net.URLDecoder.decode(kv[1], "UTF-8")
                            );
                        } catch (Exception e) {
                            // URLデコードエラーは無視
                        }
                    }
                }
            }
            return params;
        }
        
        private boolean isValidDate(String date) {
            if (date.length() != 8) return false;
            try {
                LocalDate.parse(date, DATE_FORMAT);
                return true;
            } catch (DateTimeParseException e) {
                return false;
            }
        }
        
        private synchronized void appendToFile(String date, String text) throws IOException {
            String line = date + "\t" + text + "\n";
            Files.write(Paths.get(filePath), line.getBytes("UTF-8"), 
                       StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        
        private List<String> getEntriesByDate(String targetDate) throws IOException {
            return readAllLines().stream()
                .filter(line -> line.startsWith(targetDate + "\t"))
                .collect(Collectors.toList());
        }
        
        private List<String> getEntriesByDateRange(String from, String to) throws IOException {
            return readAllLines().stream()
                .filter(line -> {
                    String[] parts = line.split("\t", 2);
                    if (parts.length < 2) return false;
                    String date = parts[0];
                    return date.compareTo(from) >= 0 && date.compareTo(to) <= 0;
                })
                .collect(Collectors.toList());
        }
        
        private List<String> getAllEntries() throws IOException {
            return readAllLines();
        }
        
        private List<String> readAllLines() throws IOException {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return new ArrayList<>();
            }
            return Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
        }
        
        private String formatEntries(List<String> entries) {
            if (entries.isEmpty()) {
                return "該当する記録がありません";
            }
            
            StringBuilder sb = new StringBuilder();
            for (String entry : entries) {
                String[] parts = entry.split("\t", 2);
                if (parts.length == 2) {
                    sb.append("日付: ").append(parts[0])
                      .append(", 内容: ").append(parts[1])
                      .append("\n");
                }
            }
            return sb.toString();
        }
        
        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            byte[] responseBytes = response.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
}