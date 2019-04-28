import java.net.DatagramPacket;
import java.net.InetAddress;

public class TCPSocketImpl extends TCPSocket {
    static final int senderPort = 9090;
    private EnhancedDatagramSocket socket;
    private short mySequenceNum = 100;
    private short anotherSideSequenceNum;
    private String destIp;
    private int destPort;

    public TCPSocketImpl() {}

    public void init(EnhancedDatagramSocket socket, String ip, int port,
            short mySeqNum, short anotherSideSeqNum) {
        this.socket = socket;
        this.destIp = ip;
        this.destPort = port;
        this.mySequenceNum = mySequenceNum;
        this.anotherSideSequenceNum = anotherSideSeqNum;
    }

    public void sendSynPacket(String ip, int port) throws Exception {
        // destination's ip and port
        this.socket = new EnhancedDatagramSocket(senderPort);

        TCPHeaderGenerator synPacket = new TCPHeaderGenerator(ip, port);
        synPacket.setSynFlag();
        synPacket.setSequenceNumber((short)this.mySequenceNum);
        this.socket.send(synPacket.getPacket());

        System.out.println("Sent Syn");
    }

    
    public void getSynAckPacket() throws Exception {
        byte bufAck[] = new byte[20];
        DatagramPacket dpAck = new DatagramPacket(bufAck, 20);

        TCPHeaderParser ackPacketParser;
        while (true) {
            socket.receive(dpAck);
            ackPacketParser = new TCPHeaderParser(dpAck.getData());
            if (ackPacketParser.isAckPacket() 
                    && ackPacketParser.isSynPacket() 
                    && ackPacketParser.getAckNumber() == this.mySequenceNum + 1)
                break;
        }

        this.anotherSideSequenceNum = ackPacketParser.getSequenceNumber();
        System.out.println(dpAck.getData()[0]);
    }

    public void sendAckPacket(String ip, int port) throws Exception {
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(ip, port);
        ackPacket.setAckFlag();
        // TODO: decide to whether user +1 or +palyload_size for AckNumber
        ackPacket.setAckNumber((short)(this.anotherSideSequenceNum + 1));
        this.socket.send(ackPacket.getPacket());

        System.out.println("Sent");
        System.out.println("Est");
    }

    public TCPSocketImpl(String ip, int port) throws Exception {
        super(ip, port);
        this.destPort = port;
        this.destIp = ip;

        sendSynPacket(ip, port);
        getSynAckPacket();
        sendAckPacket(ip, port);
    }

    @Override
    public void send(String pathToFile) throws Exception {
        // throw new RuntimeException("Not implemented!");
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        // throw new RuntimeException("Not implemented!");
    }

    @Override
    public void close() throws Exception {
        throw new RuntimeException("Not implemented!");
    }

    @Override
    public long getSSThreshold() {
        throw new RuntimeException("Not implemented!");
    }

    @Override
    public long getWindowSize() {
        throw new RuntimeException("Not implemented!");
    }
}
