package utils;

import org.json.JSONArray;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class orgJsonParsingStrategy implements ParsingStrategy {

    public List<byte[]> parse(String answer) {
        ArrayList<byte[]> parsedBytes = new ArrayList<>();
        JSONArray array = new JSONArray(answer);
        for (int i = 0; i < array.length(); i++)
            parsedBytes.add(((array.get(i)).toString() + "\n").getBytes(StandardCharsets.UTF_8));
        return parsedBytes;
    }
}
