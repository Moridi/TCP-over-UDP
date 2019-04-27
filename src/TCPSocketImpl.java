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
        // destination's ip and port
        super(ip, port);
        this.socket = new EnhancedDatagramSocket(senderPort);

        TCPHeaderGenerator synPacket = new TCPHeaderGenerator(ip, port);
        synPacket.setSynFlag();
        synPacket.setSequenceNumber((short)this.SequenceNum);
        this.socket.send(synPacket.getPacket());

        System.out.println("Sent Syn");


        byte bufAck[] = new byte[2];
        DatagramPacket dpAck = new DatagramPacket(bufAck, 2);

        while (true) {
            socket.receive(dpAck);
            TCPHeaderParser ackPacketParser = new TCPHeaderParser(dpAck.getData());
            if (ackPacketParser.isAckPacket() && ackPacketParser.isSynPacket())
                break;
        }

        System.out.println(dpAck.getData()[0]);
        
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(ip, port);
        ackPacket.setAckFlag();
        //TODO: set sequence number
        //TODO: set Ack number
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
