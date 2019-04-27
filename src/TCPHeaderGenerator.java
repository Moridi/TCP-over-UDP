// package TCPHeaderGenerator;

import java.nio.*; 
import java.net.DatagramPacket;
import java.net.InetAddress;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class TCPHeaderGenerator {
    private int MINIMUM_HEADER_SIZE = 40;
    private int SEQ_NUM_SIZE = 2;
    private int ACK_NUM_SIZE = 2;

    private byte buffer[];
    private int port;
    private String ip;

    // [0] | [1] | [2] | [3] | [4] | [5] | [6]:ACK | [7]:SYN
    public int flagIndex = 0;
    public int seqNumIndex = 1;
    public int ackNumIndex = 3;

    public TCPHeaderGenerator(String _ip, int _port) {
        this.port = _port;
        this.ip = _ip;
        this.buffer = new byte[MINIMUM_HEADER_SIZE];
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
    
    public void setSynFlag() {
        byte setSyn = 0x01;
        buffer[flagIndex] = (byte)(buffer[flagIndex] | setSyn);
    }

    public void resetSynFlag() {
        byte resetSyn = (byte)(0xFE);
        buffer[flagIndex] = (byte)(buffer[flagIndex] & resetSyn);
    }

    public void setAckFlag() {
        byte setAck = 0x02;
        buffer[flagIndex] = (byte)(buffer[flagIndex] | setAck);
    }

    public void resetAckFlag() {
        byte resetAck = (byte)(0xFD);
        buffer[flagIndex] = (byte)(buffer[flagIndex] | resetAck);
    }

    public DatagramPacket getPacket() throws Exception {
        InetAddress address = InetAddress.getByName(ip);
        return new DatagramPacket(buffer, buffer.length, address, port);
    }
}