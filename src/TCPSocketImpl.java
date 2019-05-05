import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Arrays;

public class TCPSocketImpl extends TCPSocket {
    static final int SENDER_PORT = 9090;
    static final short FIRST_SEQUENCE = 100;

    private EnhancedDatagramSocket socket;
    private short sequenceNumber;
    private short expectedSeqNumber;
    private String destIp;
    private int destPort;

    public void init(String ip, int port, short sequenceNumber) {
        this.destIp = ip;
        this.destPort = port;
        this.sequenceNumber = sequenceNumber;
    }

    public TCPSocketImpl(EnhancedDatagramSocket socket, String ip, int port,
            short mySeqNum, short expectedSeqNumber) {
        init(ip, port, mySeqNum);
        this.socket = socket;
        this.expectedSeqNumber = expectedSeqNumber;
    }

    public void sendPacketLog(String type, DatagramPacket sendingPacket) {
        System.out.println("$$$$$$ " + type + " packet sent $$$$$");
        System.out.println("To: " + sendingPacket.getAddress() + 
                " : " + Integer.toString(sendingPacket.getPort()));
        System.out.println("###\n");
    }
    
    public void getPacketLog(String type, DatagramPacket dp,
            short sequenceNumber, short expectedSeqNumber) {
        System.out.println("$$$$$$ " + type + " packet received $$$$$");
        System.out.print("Sequence number: ");
        System.out.println(sequenceNumber);
        System.out.print("Ack number: ");
        System.out.println(expectedSeqNumber);
        System.out.println("###\n");
    }

    public void sendPacket(String name, TCPHeaderGenerator packet) throws Exception {
        packet.setAckNumber(this.expectedSeqNumber);
        packet.setSequenceNumber(this.sequenceNumber);
        
        DatagramPacket sendingPacket = packet.getPacket();
        this.socket.send(sendingPacket);
        this.sequenceNumber += 1;

        sendPacketLog(name, sendingPacket);
    }

    public TCPHeaderParser receivePacket(String pathToFile) throws Exception {
        byte buffer[] = new byte[20];
        DatagramPacket packet = new DatagramPacket(buffer, 20);
        
        socket.receive(packet);
        TCPHeaderParser packetParser = new TCPHeaderParser(packet.getData());
        getPacketLog(pathToFile, packet, packetParser.getSequenceNumber(),
                packetParser.getAckNumber());
        
        return packetParser;
    }

    public void sendSynPacket() throws Exception {
        this.socket = new EnhancedDatagramSocket(SENDER_PORT);

        TCPHeaderGenerator synPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        synPacket.setSynFlag();

        sendPacket("Syn", synPacket);
    }

    public void getSynAckPacket() throws Exception {
        byte bufAck[] = new byte[20];
        DatagramPacket packet = new DatagramPacket(bufAck, 20);

        TCPHeaderParser ackPacketParser;
        while (true) {
            socket.receive(packet);
            ackPacketParser = new TCPHeaderParser(packet.getData());
            if (ackPacketParser.isAckPacket() 
                    && ackPacketParser.isSynPacket() 
                    && ackPacketParser.getAckNumber() == this.sequenceNumber)
                break;
        }

        this.expectedSeqNumber = ackPacketParser.getSequenceNumber();
        this.expectedSeqNumber += 1;

        getPacketLog("Syn/Ack", packet, ackPacketParser.getSequenceNumber(),
                ackPacketParser.getAckNumber());
    }

    public void sendAckPacket() throws Exception {
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        ackPacket.setAckFlag();

        sendPacket("Ack", ackPacket);
    }

    public TCPSocketImpl(String ip, int port) throws Exception {
        super(ip, port);
        init(ip, port, FIRST_SEQUENCE);

        sendSynPacket();
        getSynAckPacket();
        sendAckPacket();
        
        System.out.println("**** Connection established! ****\n");
    }
    
    public void sendAckPacket(String pathToFile, TCPHeaderParser packetParser)
            throws Exception {
        if (packetParser.getAckNumber() == this.sequenceNumber) {
                this.expectedSeqNumber = packetParser.getSequenceNumber();
                this.expectedSeqNumber += 1;
        }

        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        ackPacket.setAckFlag();
        
        sendPacket(pathToFile, ackPacket);
    }

    @Override
    public void send(String pathToFile) throws Exception {
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        sendPacket(pathToFile, ackPacket);
        TCPHeaderParser receivedPacket = receivePacket(pathToFile);
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        TCPHeaderParser packetParser = receivePacket(pathToFile);
        sendAckPacket(pathToFile, packetParser);
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
