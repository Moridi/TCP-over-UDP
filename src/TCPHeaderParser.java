
public class TCPHeaderParser {
    private byte buffer[];
    private int MINIMUM_HEADER_SIZE = 40;

    // [0] | [1] | [2] | [3] | [4] | [5] | [6]:ACK | [7]:SYN
    public int flagIndex = 0;

    public TCPHeaderParser(byte buffer[]) {
        this.buffer = buffer;
    }

    public boolean isSynPacket() {
        if ((buffer[flagIndex] & 0x01) == 0x01)
            return true;
        return false;
    }

    public boolean isAckPacket() {
        if ((buffer[flagIndex] & 0x02) == 0x01)
            return true;
        return false;
    }
} 