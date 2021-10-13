package utils;

import java.io.*;
import java.net.URI;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class utils {
    public final static String[] API_ENDPOINTS = {"https://api.twitter.com/1.1", "https://api.twitter.com/2"};
    public final static String[] TWEET_RETRIEVAL = {"/statuses/lookup.json?id=", "/tweets?ids="};
    public final static String[] KEY_VALIDATION = {"/application/rate_limit_status.json"};
    public final static String BEARER_TOKEN_HEADER = "Authorization";
    public final static String[] oauth_1_fields = new String[]{"API_KEY", "API_SECRET_KEY", "ACCESS_TOKEN", "ACCESS_TOKEN_SECRET"};

    private utils() {

    }

    public static void emptyFolder(String path) {
        File[] files = new File(path).listFiles();
        if (files == null) return;
        for (File x : files) {
            x.delete();
        }
    }


    public static long checkTime(HttpResponse<String> response) {
        if (response.statusCode() >= 500) throw new IllegalStateException();
        HttpHeaders header = response.headers();
        return Long.parseLong(header.firstValue("x-rate-limit-reset").toString().replaceAll("(Optional|[\\[\\]])", ""));
    }

    public static int checkLimits(HttpResponse<String> response) {
        if (response.statusCode() >= 500) throw new IllegalStateException();
        HttpHeaders header = response.headers();
        return Integer.parseInt(header.firstValue("x-rate-limit-remaining").toString().replaceAll("(Optional|[\\[\\]])", ""));
    }


    public static List<Long> loadTweetIds(String PATH) throws IOException {
        File f = new File(PATH);
        List<Long> risultato = new ArrayList<>((int) (f.length() / 20));
        BufferedReader bf = new BufferedReader(new FileReader(f));
        try {
            String text;
            Pattern pattern = Pattern.compile("[0-9]+");
            Matcher matcher;
            while (true) {
                text = bf.readLine();
                if (text == null) break;
                matcher = pattern.matcher(text);
                if (!matcher.matches()) continue;
                risultato.add(Long.parseLong(text));
            }
            bf.close();
            risultato.sort(Long::compare);
            return Collections.unmodifiableList(risultato);
        } finally {
            bf.close();
        }
    }

    private static void checkSubFoldersRecursively(ArrayList<File> tweets, File file) {
        File[] files = file.listFiles();
        if (files == null) return;
        Arrays.stream(files).forEach(f -> {
            if (!f.isDirectory() && f.getName().matches(".*\\.txt")) {
                tweets.add(f);
            } else if (f.isDirectory()) checkSubFoldersRecursively(tweets, f);
        });
    }

    public static List<File> loadFiles(File inputPath) throws IOException {
        if (inputPath.isDirectory()) {
            ArrayList<File> tweets = new ArrayList<>();
            checkSubFoldersRecursively(tweets, inputPath);
            if (tweets.size() == 0)
                throw new IOException("Bad directory specified");
            return tweets;
        }
        if (inputPath.isFile()) return Collections.singletonList(inputPath);
        BufferedReader br = new BufferedReader(new FileReader(inputPath));
        try {
            String line = "";
            ArrayList<File> tweetIdFiles = new ArrayList<>();
            while (true) {
                line = br.readLine();
                if (line == null) break;
                tweetIdFiles.add(new File(line));
            }
            tweetIdFiles.sort(File::compareTo);
            return tweetIdFiles;
        } finally {
            br.close();
        }
    }


    public static URI generateQuery(List<Long> ids, int API_VERSION) {
        StringBuilder sb = new StringBuilder(API_ENDPOINTS[API_VERSION - 1] + TWEET_RETRIEVAL[API_VERSION - 1]);
        for (Long x : ids)
            sb.append(x).append(",");
        return URI.create(sb.deleteCharAt(sb.length() - 1).append("&tweet_mode=extended").toString());
    }


}