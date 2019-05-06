import java.net.DatagramPacket;

public class TCPServerSocketImpl extends TCPServerSocket {
    static final short FIRST_SEQUENCE = 200;

    private EnhancedDatagramSocket socket;
    private short expectedSeqNumber;
    private short nextSeqNumber;
    private short sendBase;

    public TCPServerSocketImpl(int port) throws Exception {
        super(port);
        this.socket = new EnhancedDatagramSocket(port);
        this.nextSeqNumber = FIRST_SEQUENCE;
        this.sendBase = FIRST_SEQUENCE;
    }
    
    public void packetLog(String type, String name) {
        System.out.println(type + ": " + name +
                ", Exp: " + Integer.toString(this.expectedSeqNumber) +
                ", Send base: " + Integer.toString(this.sendBase) +
                ", NextSeq: " + Integer.toString(this.nextSeqNumber));
    }
    public DatagramPacket getSynPacket() throws Exception {
        byte buf[] = new byte[20];
        DatagramPacket dp = new DatagramPacket(buf, 20);
        TCPHeaderParser dpParser;
        
        while (true) {
            socket.receive(dp);
            dpParser = new TCPHeaderParser(dp.getData(), dp.getLength());
            if (dpParser.isSynPacket())
                break;
        }
        packetLog("Receiver", "Syn");

        this.expectedSeqNumber = (short)(dpParser.getSequenceNumber() + 1);

        return dp;
    }

    public TCPHeaderGenerator setupSynAckPacket(DatagramPacket dp) throws Exception {
        String hostAddress = dp.getAddress().getHostAddress();
        int hostPort = dp.getPort();

        TCPHeaderGenerator synAckPacket = new TCPHeaderGenerator(hostAddress, hostPort);
        synAckPacket.setSynFlag();
        synAckPacket.setAckFlag();
        synAckPacket.setAckNumber(this.expectedSeqNumber);

        return synAckPacket;
    }

    public void getAckPacket() throws Exception{

        byte ackBuf[] = new byte[20];
        DatagramPacket ackPacket = new DatagramPacket(ackBuf, 20);
        TCPHeaderParser ackPacketParser;
        
        while (true) {
            socket.receive(ackPacket);
            ackPacketParser = new TCPHeaderParser(ackPacket.getData(), ackPacket.getLength());
    
            if (ackPacketParser.isAckPacket() 
                    && (ackPacketParser.getSequenceNumber() == this.expectedSeqNumber))
                break;
        }
        packetLog("Receiver", "Ack");

        this.expectedSeqNumber += 1;
        this.sendBase = ackPacketParser.getAckNumber();
    }   

    public TCPSocketImpl createTcpSocket(DatagramPacket dp) {
        String hostAddress = dp.getAddress().getHostAddress();
        int hostPort = dp.getPort();

        TCPSocketImpl tcpSocket = new TCPSocketImpl(socket,
                hostAddress, hostPort, this.expectedSeqNumber, this.sendBase);

        System.out.println("**** Connection established! ****\n");

        return tcpSocket;
    } 

    public void setAckNumber(DatagramPacket dp) {
        TCPHeaderParser dpParser = new TCPHeaderParser(dp.getData(), dp.getLength());
        this.expectedSeqNumber = dpParser.getSequenceNumber();
    }

    public void sendSynAckPacket(DatagramPacket packet) throws Exception {
        String hostAddress = packet.getAddress().getHostAddress();
        int hostPort = packet.getPort();

        TCPHeaderGenerator synAckPacket = new TCPHeaderGenerator(hostAddress, hostPort);
        synAckPacket.setSynFlag();
        synAckPacket.setAckFlag();

        synAckPacket.setSequenceNumber(this.nextSeqNumber);
        synAckPacket.setAckNumber(this.expectedSeqNumber);

        DatagramPacket sendingPacket = synAckPacket.getPacket();
        this.socket.send(sendingPacket);
        packetLog("Sender", "Syn/Ack");

        this.nextSeqNumber += 1;
    }

    @Override
    public TCPSocket accept() throws Exception {
        DatagramPacket dp = getSynPacket();
        sendSynAckPacket(dp);
        getAckPacket();

        return createTcpSocket(dp);
    }

    @Override
    public void close() throws Exception {
        throw new RuntimeException("Not implemented!");
    }
}
