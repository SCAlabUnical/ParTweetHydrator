package strategy;

import flyweight.FlyweightFactory;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class VerboseParsing extends AbstractStrategy {
    private static Set<Long> uniqueIds = Collections.synchronizedSet(new HashSet<>(10000000));

    public static Set<Long> getUniqueIds() {
        return Collections.unmodifiableSet(uniqueIds);
    }

    public VerboseParsing() {

    }

    public VerboseParsing(FlyweightFactory flyweightFactory) {
        super(flyweightFactory);
    }

    @Override
    public Collection<byte[]> parse(String s) {
        Collection<byte[]> parsedBytes;
        if (flyweightFactory != null)
            parsedBytes = flyweightFactory.getFlyweight();
        else parsedBytes = new ArrayList<>();
        JSONObject object = new JSONObject(s);
        JSONObject array = object.getJSONObject("id");
        String[] fields = JSONObject.getNames(array);
        Arrays.stream(fields).forEach(field -> {
            if (!array.isNull(field))
                parsedBytes.add((array.get(field) + "\n").getBytes(StandardCharsets.UTF_8));
            uniqueIds.add(Long.parseLong(field));
        });
        return parsedBytes;
    }
}
