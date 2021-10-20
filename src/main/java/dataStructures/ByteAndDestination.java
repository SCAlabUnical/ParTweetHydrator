package dataStructures;

import java.util.Collection;

public record ByteAndDestination(Collection<byte[]> array, int fileIndex, int packetN){

}
