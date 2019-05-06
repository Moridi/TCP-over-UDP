import java.net.DatagramPacket;

public class TCPSocketImpl extends TCPSocket {
    static final int SENDER_PORT = 9090;
    static final short FIRST_SEQUENCE = 100;
    static final short WINDOW_SIZE = 5;
    static final int MIN_BUFFER_SIZE = 80;

    private EnhancedDatagramSocket socket;
    private short expectedSeqNumber;

    private short sendBase;
    private short nextSeqNumber;

    private String destIp;
    private int destPort;

    private short windowSize;
    private byte tempData[];

    public void init(String ip, int port, short base) {
        this.destIp = ip;
        this.destPort = port;
        this.windowSize = WINDOW_SIZE;
        this.sendBase = base;
        this.nextSeqNumber = base;

        this.tempData = new byte[2 * WINDOW_SIZE];
        for (int i = 0; i < this.tempData.length; ++i) {
            tempData[i] = (byte)i;
        }
    }

    public TCPSocketImpl(EnhancedDatagramSocket socket, String ip, int port,
            short expectedSeqNumber, short base) {
        init(ip, port, base);
        this.socket = socket;
        this.expectedSeqNumber = expectedSeqNumber;
    }
    
    public void packetLog(String type, String name) {
        System.out.println(type + ": " + name +
                ", Exp: " + Integer.toString(this.expectedSeqNumber) +
                ", Send base: " + Integer.toString(this.sendBase) +
                ", NextSeq: " + Integer.toString(this.nextSeqNumber));
    }

    public void sendPacket(String name, TCPHeaderGenerator packet) throws Exception {
        packet.setSequenceNumber(this.nextSeqNumber);
        packet.setAckNumber(this.expectedSeqNumber);
        
        DatagramPacket sendingPacket = packet.getPacket();
        this.socket.send(sendingPacket);
        packetLog("Sender", name);

        this.nextSeqNumber += 1;
    }

    public TCPHeaderParser receivePacket(String pathToFile) throws Exception {
        byte buffer[] = new byte[MIN_BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, MIN_BUFFER_SIZE);
        
        socket.receive(packet);
        packetLog("Receiver", pathToFile);

        TCPHeaderParser packetParser = new TCPHeaderParser(packet.getData(),
                packet.getLength());

        return packetParser;
    }

    public void sendSynPacket() throws Exception {
        this.socket = new EnhancedDatagramSocket(SENDER_PORT);

        TCPHeaderGenerator synPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        synPacket.setSynFlag();

        synPacket.setSequenceNumber(this.nextSeqNumber);
        
        DatagramPacket sendingPacket = synPacket.getPacket();
        this.socket.send(sendingPacket);
        packetLog("Sender", "Syn");

        this.nextSeqNumber += 1;
    }

    public void getSynAckPacket() throws Exception {
        byte bufAck[] = new byte[MIN_BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(bufAck, MIN_BUFFER_SIZE);

        TCPHeaderParser ackPacketParser;
        while (true) {
            socket.receive(packet);
            ackPacketParser = new TCPHeaderParser(packet.getData(), packet.getLength());

            if (ackPacketParser.isAckPacket() && ackPacketParser.isSynPacket())
                break;
        }
        
        packetLog("Receiver", "Syn/Ack");

        this.sendBase = ackPacketParser.getAckNumber();
        this.expectedSeqNumber = (short)(ackPacketParser.getSequenceNumber() + 1);
    }

    public void sendAckPacket() throws Exception {
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        ackPacket.setAckFlag();

        ackPacket.setSequenceNumber(this.nextSeqNumber);
        ackPacket.setAckNumber(this.expectedSeqNumber);
        
        DatagramPacket sendingPacket = ackPacket.getPacket();
        this.socket.send(sendingPacket);
        packetLog("Sender", "Ack");
        this.nextSeqNumber += 1;
        this.sendBase += 1;
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

        packetLog("Sending part", "Start");
        // for (int i = 0; i < tempData.length; ++i) {
        //     if (this.nextSeqNumber < this.sendBase + windowSize) {
        //         // if (expectedSeqNumber == this.nextSeqNumber)
        //             // Start the timer 

        //         TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        //         ackPacket.addData(tempData[i]);
        //         sendPacket("** : " + Integer.toString(i), ackPacket);

        //         TCPHeaderParser receivedPacket = receivePacket("## : " + Integer.toString(i));
                
        //         if (receivedPacket.isAckPacket()) {
        //             this.sendBase = (short)(receivedPacket.getAckNumber() + 1);
        //             // if (sendBase == nextSeqNumber)
        //                 // Stop the timer
        //             // else
        //                 // Start the timer 
                        
        //             this.expectedSeqNumber += 1;
        //         }
        //     }
        // }
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        packetLog("Receiving part", "Start");

        // for (int i = 0; i < tempData.length; ++i) {
        //     TCPHeaderParser packetParser = receivePacket("## : " + Integer.toString(i));

        //     // System.out.println("hasseqnum(rcvpkt,expectedseqnum): " +
        //     //         packetParser.getSequenceNumber() + ", Expected: " +
        //     //         this.expectedSeqNumber);

        //     this.expectedSeqNumber += 1;
        //     sendAckPacket("** : " + Integer.toString(i), packetParser);
        // }
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
