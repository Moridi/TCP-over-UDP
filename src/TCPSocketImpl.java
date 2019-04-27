import java.net.DatagramPacket;
import java.net.InetAddress;

public class TCPSocketImpl extends TCPSocket {
    static final int senderPort = 9090;
    private EnhancedDatagramSocket socket;
    private short SequenceNum = 1000;
    private String destIp;
    private int destPort;

    public TCPSocketImpl() {}

    public void init(EnhancedDatagramSocket socket, String ip, int port) {
        this.socket = socket;
        this.destIp = ip;
        this.destPort = port;
    }

    public TCPSocketImpl(String ip, int port) throws Exception {
        super(ip, port);

        short recvSeqNum;
        // destination's ip and port
        this.socket = new EnhancedDatagramSocket(senderPort);

        TCPHeaderGenerator synPacket = new TCPHeaderGenerator(ip, port);
        synPacket.setSynFlag();
        synPacket.setSequenceNumber((short)this.SequenceNum);
        this.socket.send(synPacket.getPacket());

        System.out.println("Sent Syn");


        byte bufAck[] = new byte[20];
        DatagramPacket dpAck = new DatagramPacket(bufAck, 20);

        TCPHeaderParser ackPacketParser;
        while (true) {
            socket.receive(dpAck);
            ackPacketParser = new TCPHeaderParser(dpAck.getData());
            if (ackPacketParser.isAckPacket() 
                    && ackPacketParser.isSynPacket() 
                    && ackPacketParser.getAckNumber() == this.SequenceNum + 1)
                break;
        }

        recvSeqNum = ackPacketParser.getSequenceNumber();
        System.out.println(dpAck.getData()[0]);
        
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(ip, port);
        ackPacket.setAckFlag();
        // TODO: decide to whether user +1 or +palyload_size for AckNumber
        ackPacket.setAckNumber((short)(recvSeqNum + 1));
        this.socket.send(ackPacket.getPacket());

        System.out.println("Sent");
        System.out.println("Est");

        this.destPort = port;
        this.destIp = ip;
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
