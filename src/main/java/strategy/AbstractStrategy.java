package strategy;

import flyweight.FlyweightFactory;

public abstract class AbstractStrategy implements ParsingStrategy {
    protected FlyweightFactory flyweightFactory = null;

    public AbstractStrategy() {}

    public AbstractStrategy(FlyweightFactory flyweightFactory) {
        this.flyweightFactory = flyweightFactory;
    }
}
