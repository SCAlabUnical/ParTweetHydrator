package utils;

import java.util.List;

public interface ParsingStrategy {
    List<byte[]> parse(String s);
}
