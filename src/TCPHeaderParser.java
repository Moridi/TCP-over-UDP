
import java.nio.*; 
import java.util.Arrays;

public class TCPHeaderParser {
    private byte buffer[];
    private int MIN_HEADER_SIZE = 40;
    private int SEQ_NUM_SIZE = 2;
    private int ACK_NUM_SIZE = 2;
    private int RWND_SIZE = 2;

    // [0] | [1] | [2] | [3] | [4] | [5] | [6]:ACK | [7]:SYN
    public int flagIndex = 0;
    public int seqNumIndex = 1;
    public int ackNumIndex = 3;
    private int rwndIndex = 5;

    public TCPHeaderParser(byte buffer[], int length) {
        this.buffer = Arrays.copyOfRange(buffer, 0, length);
    }

    public boolean isSynPacket() {
        if ((buffer[flagIndex] & 0x01) == 0x01)
            return true;
        return false;
    }

    public boolean isAckPacket() {
        if ((buffer[flagIndex] & 0x02) == 0x02)
            return true;
        return false;
    }

    public short getSequenceNumber() {
        byte[] arr = new byte[SEQ_NUM_SIZE];

        for (int i = 0; i < SEQ_NUM_SIZE; ++i)
            arr[i] = buffer[i + seqNumIndex];

        ByteBuffer wrapped = ByteBuffer.wrap(arr); // big-endian by default
        short num = wrapped.getShort();

        return num;
    }

    public short getRwnd() {
        byte[] arr = new byte[RWND_SIZE];

        for (int i = 0; i < RWND_SIZE; ++i)
            arr[i] = buffer[i + rwndIndex];

        ByteBuffer wrapped = ByteBuffer.wrap(arr); // big-endian by default
        short num = wrapped.getShort();

        return num;
    }

    public short getAckNumber() {
        byte[] arr = new byte[ACK_NUM_SIZE];

        for (int i = 0; i < ACK_NUM_SIZE; ++i)
            arr[i] = buffer[i + ackNumIndex];

        ByteBuffer wrapped = ByteBuffer.wrap(arr); // big-endian by default
        short num = wrapped.getShort();

        return num;
    }

    public byte[] getData() {
        byte[] data = new byte[buffer.length - MIN_HEADER_SIZE];

        for (int i = 0; i < data.length; ++i) {
            data[i] = buffer[MIN_HEADER_SIZE + i];
        }
        return data;
    }
}