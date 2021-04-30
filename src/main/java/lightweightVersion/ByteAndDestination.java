package lightweightVersion;

import java.io.File;
import java.util.List;

public record ByteAndDestination(List<byte[]> array,File output,int packetN){

}
