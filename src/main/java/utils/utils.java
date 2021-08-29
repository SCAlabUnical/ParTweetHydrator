package utils;

import lightweightVersion.Key;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.URI;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;

import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

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
        if(files == null) return;
        Arrays.stream(files).forEach(f -> {
            if(!f.isDirectory() && f.getName().matches(".*\\.txt")){
                tweets.add(f);
            } else checkSubFoldersRecursively(tweets,f);
        });
    }

    public static List<File> loadFiles(File inputPath) throws IOException {
        if (inputPath.isDirectory()) {
            ArrayList<File> tweets = new ArrayList<>();
            checkSubFoldersRecursively(tweets,inputPath);
            if (tweets.size() == 0)
                throw new IOException("Bad directory specified");
            return tweets;
        }
        if(inputPath.isFile()) return Collections.singletonList(inputPath);
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


    public static Key[] loadAllTokens(String XMLPATH) throws Key.UnusableKeyException, IOException {
        ArrayList<Key> tokens = new ArrayList<>(100);
        try {
            int bearer = 0, oauth1 = 0;
            Document xmlDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(XMLPATH));
            String expr = "//BearerToken/text()";
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.evaluate(expr, xmlDocument, XPathConstants.NODESET);
            System.out.println("Recuperati " + (bearer = nodeList.getLength()) + " bearer token da " + XMLPATH);
            for (int i = 0; i < nodeList.getLength(); i++)
                tokens.add(new Key("Bearer " + nodeList.item(i).getNodeValue().trim()));
            expr = "//Progetto";
            xPath = XPathFactory.newInstance().newXPath();
            nodeList = (NodeList) xPath.evaluate(expr, xmlDocument, XPathConstants.NODESET);
            System.out.println("Recuperati " + (oauth1 = nodeList.getLength()) + " oauth1 token da " + XMLPATH);
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node project = nodeList.item(i);
                String[] oauthValues = new String[oauth_1_fields.length];
                for (int j = 0; j < oauthValues.length; j++) {
                    xPath = XPathFactory.newInstance().newXPath();
                    oauthValues[j] = ((String) xPath.evaluate(("./" + oauth_1_fields[j] + "/text()"), project, XPathConstants.STRING)).trim();
                }
                tokens.add(new Key(Arrays.copyOf(oauthValues, oauthValues.length)));
            }
            System.out.println("Per ogni finestra di 15 minuti è possibile idratare " + (bearer * 300 + oauth1 * 900) * 100 + " tweet");
        } catch (SAXException | ParserConfigurationException | XPathExpressionException e) {
            throw new IOException("File structure not valid,check github for a fac-simile");
        }
        Collections.sort(tokens, Key::compareTo);
        return tokens.toArray(new Key[0]);
    }


}