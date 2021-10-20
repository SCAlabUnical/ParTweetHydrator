package utils;

import hydrator.Hydrator;
import key.AbstractKey;
import key.BearerToken;
import key.OAuth1Token;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.net.URI;

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


    public static List<String> loadTweetIds(String PATH) throws IOException {
        File f = new File(PATH);
        List<String> risultato = new ArrayList<>((int) (f.length() / 20));
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
                risultato.add((text.trim()));
            }
            bf.close();
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

    public static AbstractKey[] loadAllTokens(String XMLPATH) {
        ArrayList<AbstractKey> tokens = new ArrayList<>(100);
        try {
            int bearer = 0, oauth1 = 0;
            AbstractKey currToken;
            Document xmlDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(XMLPATH));
            String expr = "//BearerToken/text()";
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.evaluate(expr, xmlDocument, XPathConstants.NODESET);
            System.out.println("Found " + (bearer = nodeList.getLength()) + " bearer tokes in " + XMLPATH);
            for (int i = 0; i < nodeList.getLength(); i++)
                try {
                    currToken = new BearerToken("Bearer " + nodeList.item(i).getNodeValue().trim());
                    tokens.add(currToken);
                } catch (AbstractKey.UnusableKeyException e) {
                    System.out.println(nodeList.item(i).getNodeValue().trim() + "Is not a valid Bearer token");
                }
            expr = "//Progetto";
            xPath = XPathFactory.newInstance().newXPath();
            nodeList = (NodeList) xPath.evaluate(expr, xmlDocument, XPathConstants.NODESET);
            System.out.println("Found " + (oauth1 = nodeList.getLength()) + " sets of  oauth1 tokes in " + XMLPATH);
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node project = nodeList.item(i);
                String[] oauthValues = new String[oauth_1_fields.length];
                for (int j = 0; j < oauthValues.length; j++) {
                    xPath = XPathFactory.newInstance().newXPath();
                    oauthValues[j] = ((String) xPath.evaluate(("./" + oauth_1_fields[j] + "/text()"), project, XPathConstants.STRING)).trim();
                }
                try {
                    currToken = new OAuth1Token(Arrays.copyOf(oauthValues, oauthValues.length));
                    tokens.add(currToken);
                } catch (AbstractKey.UnusableKeyException e) {
                    System.out.println(Arrays.toString(oauthValues) + "Is not a valid set of oauth1 tokens");
                }
            }
            Hydrator.INSTANCE.setCurrentWorkRate((bearer * 300 + oauth1 * 900) * 100);
        } catch (SAXException | ParserConfigurationException | XPathExpressionException | IOException e) {
            throw new RuntimeException("File structure invalid,check github for a fac-simile");
        }
        Collections.shuffle(tokens, new Random(System.currentTimeMillis()));
        return tokens.toArray(new AbstractKey[tokens.size()]);
    }

    public static URI generateQuery(List<String> ids, int API_VERSION) {
        StringBuilder sb = new StringBuilder(API_ENDPOINTS[API_VERSION - 1] + TWEET_RETRIEVAL[API_VERSION - 1]);
        for (String x : ids)
            sb.append(x).append(",");
        return URI.create(sb.deleteCharAt(sb.length() - 1).append("&tweet_mode=extended&map=true").toString());
    }

    

}