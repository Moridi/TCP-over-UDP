import java.net.DatagramPacket;
import java.net.InetAddress;

import java.nio.ByteBuffer;

public class TCPHeaderGenerator {
    private int MIN_HEADER_SIZE = 40;
    private int MIN_PAYLOAD_SIZE = 40;
    private int SEQ_NUM_SIZE = 2;
    private int ACK_NUM_SIZE = 2;
    private int RWND_SIZE = 2;

    private byte buffer[];
    private int port;
    private String ip;
    private int currentDataIndex;

    // [0] | [1] | [2] | [3] | [4] | [5] | [6]:ACK | [7]:SYN
    private int flagIndex = 0;
    private int seqNumIndex = 1;
    private int ackNumIndex = 3;
    private int rwndIndex = 5;

    public TCPHeaderGenerator(String _ip, int _port) {
        this.port = _port;
        this.ip = _ip;
        this.buffer = new byte[MIN_HEADER_SIZE + MIN_PAYLOAD_SIZE];
        this.currentDataIndex = MIN_HEADER_SIZE;
        buffer[flagIndex] = 0x00;
    }
    public void setSequenceNumber(short seqNumber) {        
        ByteBuffer dbuf = ByteBuffer.allocate(SEQ_NUM_SIZE);
        dbuf.putShort(seqNumber);
        byte[] bytes = dbuf.array();

        for (int i = 0; i < SEQ_NUM_SIZE; i++)
            buffer[seqNumIndex + i] = bytes[i];
    }

    public void setAckNumber(short ackNumber) {        
        ByteBuffer dbuf = ByteBuffer.allocate(ACK_NUM_SIZE);
        dbuf.putShort(ackNumber);
        byte[] bytes = dbuf.array();

        for (int i = 0; i < ACK_NUM_SIZE; i++)
            buffer[ackNumIndex + i] = bytes[i];
    }

    public void setRwnd(short rwnd) {        
        ByteBuffer dbuf = ByteBuffer.allocate(RWND_SIZE);
        dbuf.putShort(rwnd);
        byte[] bytes = dbuf.array();

        for (int i = 0; i < RWND_SIZE; i++)
            buffer[rwndIndex + i] = bytes[i];
    }
    
    public void setSynFlag() {
        byte setSyn = 0x01;
        buffer[flagIndex] = (byte)(buffer[flagIndex] | setSyn);
    }

    public void setAckFlag() {
        byte setAck = 0x02;
        buffer[flagIndex] = (byte)(buffer[flagIndex] | setAck);
    }

    public DatagramPacket getPacket() throws Exception {
        InetAddress address = InetAddress.getByName(ip);
        return new DatagramPacket(buffer, currentDataIndex, address, port);
    }

    public void addData(byte newData) {
        buffer[currentDataIndex] = newData;
        currentDataIndex++;
    }

    public void addData(byte[] newData) {
        for(int i=0; i < newData.length; i++) {
            this.addData(newData[i]);
        }
    }

}