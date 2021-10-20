package strategy;

import java.util.Collection;
import java.util.List;

public interface ParsingStrategy {
    Collection<byte[]> parse(String s);
}
