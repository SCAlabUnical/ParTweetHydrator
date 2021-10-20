package strategy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

@Deprecated
public class customParsingStrategy implements ParsingStrategy {

    public List<byte[]> parse(String answer) {
        Stack<Character> parser = new Stack<>();
        int beginIndex = 0;
        ArrayList<byte[]> arrayList = new ArrayList<>(100);
        boolean firstMatch = true;
        char peek = ' ', c;
        for (int i = 0; i < answer.length(); i++) {
            c = answer.charAt(i);
            if (c == '}' && peek == '{') {
                parser.pop();
                if (parser.isEmpty()) {
                    arrayList.add((answer.substring(beginIndex, i + 1) + "\n").getBytes(StandardCharsets.UTF_8));
                    firstMatch = true;
                } else peek = parser.peek();
            } else if (c == '}' || c == '{') {
                if (firstMatch && c == '{') {
                    beginIndex = i;
                    firstMatch = false;
                }
                parser.push(peek = c);
            }

        }
        return arrayList;
    }

}
