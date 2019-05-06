import java.net.DatagramPacket;
import java.util.Arrays;

public class TCPSocketImpl extends TCPSocket {
    static final int SENDER_PORT = 9090;
    static final short FIRST_SEQUENCE = 100;
    static final short WINDOW_SIZE = 5;
    static final int MIN_BUFFER_SIZE = 80;

    private EnhancedDatagramSocket socket;
    private short nextSeqNumber;
    private short expectedSeqNumber;
    private String destIp;
    private int destPort;

    private short sendBase;
    private short windowSize;
    private byte tempData[];

    public void init(String ip, int port, short nextSeqNumber) {
        this.destIp = ip;
        this.destPort = port;
        this.nextSeqNumber = nextSeqNumber;
        this.windowSize = WINDOW_SIZE;
        this.sendBase = nextSeqNumber;

        this.tempData = new byte[2 * WINDOW_SIZE];
        for (int i = 0; i < this.tempData.length; ++i) {
            tempData[i] = (byte)i;
        }
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
            short nextSeqNumber, short expectedSeqNumber, byte data[]) {
        System.out.println("$$$$$$ " + type + " packet received $$$$$");
        System.out.println("Data: ");
        for (int i = 0; i < data.length; ++i) {
            System.out.print(data[i]);
            System.out.print(", ");
        }
        System.out.println("");

        System.out.print("Sequence number: ");
        System.out.println(nextSeqNumber);
        System.out.print("Ack number: ");
        System.out.println(expectedSeqNumber);
        System.out.println("###\n");
    }

    public void sendPacket(String name, TCPHeaderGenerator packet) throws Exception {
        packet.setAckNumber(this.expectedSeqNumber);
        packet.setSequenceNumber(this.nextSeqNumber);
        
        DatagramPacket sendingPacket = packet.getPacket();
        this.socket.send(sendingPacket);
        this.nextSeqNumber += 1;

        sendPacketLog(name, sendingPacket);
    }

    public TCPHeaderParser receivePacket(String pathToFile) throws Exception {
        byte buffer[] = new byte[MIN_BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, MIN_BUFFER_SIZE);
        
        socket.receive(packet);
        TCPHeaderParser packetParser = new TCPHeaderParser(packet.getData(), packet.getLength());
        getPacketLog(pathToFile, packet, packetParser.getSequenceNumber(),
                packetParser.getAckNumber(), packetParser.getData());
   
        
        return packetParser;
    }

    public void sendSynPacket() throws Exception {
        this.socket = new EnhancedDatagramSocket(SENDER_PORT);

        TCPHeaderGenerator synPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        synPacket.setSynFlag();

        sendPacket("Syn", synPacket);
    }

    public void getSynAckPacket() throws Exception {
        byte bufAck[] = new byte[MIN_BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(bufAck, MIN_BUFFER_SIZE);

        TCPHeaderParser ackPacketParser;
        while (true) {
            socket.receive(packet);
            ackPacketParser = new TCPHeaderParser(packet.getData(), packet.getLength());
            if (ackPacketParser.isAckPacket() 
                    && ackPacketParser.isSynPacket() 
                    && ackPacketParser.getAckNumber() == this.nextSeqNumber)
                break;
        }

        this.expectedSeqNumber = ackPacketParser.getSequenceNumber();
        this.expectedSeqNumber += 1;

        getPacketLog("Syn/Ack", packet, ackPacketParser.getSequenceNumber(),
                ackPacketParser.getAckNumber(), ackPacketParser.getData());
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
        if (packetParser.getAckNumber() == this.nextSeqNumber) {
                this.expectedSeqNumber = packetParser.getSequenceNumber();
                this.expectedSeqNumber += 1;
        }

        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        ackPacket.setAckFlag();
        
        sendPacket(pathToFile, ackPacket);
    }

    @Override
    public void send(String pathToFile) throws Exception {
        for (int i = 0; i < tempData.length; ++i) {
            TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
            ackPacket.addData(tempData[i]);
            sendPacket("** : " + Integer.toString(i), ackPacket);
            TCPHeaderParser receivedPacket = receivePacket("## : " + Integer.toString(i));
        }
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        for (int i = 0; i < tempData.length; ++i) {
            TCPHeaderParser packetParser = receivePacket("## : " + Integer.toString(i));
            sendAckPacket("** : " + Integer.toString(i), packetParser);
        }
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
