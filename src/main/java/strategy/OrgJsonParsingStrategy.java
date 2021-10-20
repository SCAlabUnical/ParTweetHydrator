package strategy;

import flyweight.FlyweightFactory;
import org.json.JSONArray;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;


public class OrgJsonParsingStrategy extends AbstractStrategy {
    public OrgJsonParsingStrategy() {

    }

    public OrgJsonParsingStrategy(FlyweightFactory flyweightFactory) {
        super(flyweightFactory);
    }

    public Collection<byte[]> parse(String answer) {
        Collection<byte[]> parsedBytes;
        if (flyweightFactory != null)
            parsedBytes = flyweightFactory.getFlyweight();
        else parsedBytes = new ArrayList<>();
        JSONArray array = new JSONArray(answer);
        for (int i = 0; i < array.length(); i++)
            parsedBytes.add(((array.get(i)).toString() + "\n").getBytes(StandardCharsets.UTF_8));
        return parsedBytes;
    }
}
