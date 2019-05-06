import java.net.DatagramPacket;

public class TCPSocketImpl extends TCPSocket {
    static final int SENDER_PORT = 9090;
    static final short FIRST_SEQUENCE = 100;
    static final short WINDOW_SIZE = 5;
    static final int MIN_BUFFER_SIZE = 80;

    private EnhancedDatagramSocket socket;
    private short mySeqNumber;
    private short nextSeqNumber;
    private short expectedSeqNumber;
    private String destIp;
    private int destPort;

    private short windowSize;
    private byte tempData[];

    public void init(String ip, int port, short seqNumber) {
        this.destIp = ip;
        this.destPort = port;
        this.mySeqNumber = seqNumber;
        this.windowSize = WINDOW_SIZE;

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
        nextSeqNumber = expectedSeqNumber;
    }
    
    public void packetLog(String type, String name) {

        System.out.println(type + ": " + name + ", MySeq: " + Integer.toString(this.mySeqNumber) +
        ", NextSeq: " + Integer.toString(this.nextSeqNumber) +
        ", Exp: " + Integer.toString(this.expectedSeqNumber));
    }

    public void sendPacket(String name, TCPHeaderGenerator packet) throws Exception {
        packet.setAckNumber(this.expectedSeqNumber);
        packet.setSequenceNumber(this.mySeqNumber);
        
        DatagramPacket sendingPacket = packet.getPacket();
        this.socket.send(sendingPacket);
        packetLog("Sender", name);

        this.mySeqNumber += 1;
        this.nextSeqNumber += 1;
    }

    public TCPHeaderParser receivePacket(String pathToFile) throws Exception {
        byte buffer[] = new byte[MIN_BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, MIN_BUFFER_SIZE);
        
        socket.receive(packet);
        packetLog("Receiver", pathToFile);

        TCPHeaderParser packetParser = new TCPHeaderParser(packet.getData(), packet.getLength());

        if (packetParser.getAckNumber() == this.mySeqNumber) {
            // this.expectedSeqNumber = packetParser.getSequenceNumber();
            this.expectedSeqNumber += 1;
        }
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
                    && ackPacketParser.getAckNumber() == this.mySeqNumber)
                break;
        }
        
        packetLog("Receiver", "Syn/Ack");

        this.expectedSeqNumber = ackPacketParser.getSequenceNumber();
        this.nextSeqNumber = ackPacketParser.getSequenceNumber();
        this.expectedSeqNumber += 1;
        this.nextSeqNumber += 1;

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
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        ackPacket.setAckFlag();
        
        sendPacket(pathToFile, ackPacket);
    }

    @Override
    public void send(String pathToFile) throws Exception {
        for (int i = 0; i < tempData.length; ++i) {
            if (this.nextSeqNumber < expectedSeqNumber + windowSize) {
                // if (expectedSeqNumber == this.nextSeqNumber)
                    // Start the timer 

                TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
                ackPacket.addData(tempData[i]);
                sendPacket("** : " + Integer.toString(i), ackPacket);

                TCPHeaderParser receivedPacket = receivePacket("## : " + Integer.toString(i));

                // if (expectedSeqNumber == this.nextSeqNumber)
                    // Stop the timer
                // else
                    // Start the timer 
            }
            else
                i--;
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
