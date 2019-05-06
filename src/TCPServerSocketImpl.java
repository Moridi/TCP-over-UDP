import java.net.DatagramPacket;

public class TCPServerSocketImpl extends TCPServerSocket {
    static final short FIRST_SEQUENCE = 200;
    
    private EnhancedDatagramSocket socket;
    private short sequenceNumber;
    private short expectedSeqNumber;

    public TCPServerSocketImpl(int port) throws Exception {
        super(port);
        this.socket = new EnhancedDatagramSocket(port);
        this.sequenceNumber = FIRST_SEQUENCE;
    }
    
    public void packetLog(String type, String name) {

        System.out.println(type + ": " + name + ", MySeq: " + Integer.toString(this.sequenceNumber) +
                ", Exp: " + Integer.toString(this.expectedSeqNumber));
    }

    public void sendPacket(String name, TCPHeaderGenerator packet) throws Exception {
        packet.setAckNumber(this.expectedSeqNumber);
        packet.setSequenceNumber(this.sequenceNumber);
        
        DatagramPacket sendingPacket = packet.getPacket();
        this.socket.send(sendingPacket);
        packetLog("Sender", name);
        this.sequenceNumber += 1;
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

        this.expectedSeqNumber = dpParser.getSequenceNumber();
        this.expectedSeqNumber += 1;


        return dp;
    }

    public TCPHeaderGenerator setupSynAckPacket(DatagramPacket dp) throws Exception {
        String hostAddress = dp.getAddress().getHostAddress();
        int hostPort = dp.getPort();

        TCPHeaderGenerator synAckPacket = new TCPHeaderGenerator(hostAddress, hostPort);
        synAckPacket.setSynFlag();
        synAckPacket.setAckFlag();
        synAckPacket.setSequenceNumber(this.sequenceNumber);
        synAckPacket.setAckNumber(this.expectedSeqNumber);

        this.sequenceNumber += 1;

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
                    && (ackPacketParser.getAckNumber() == this.sequenceNumber))
                break;
        }
        packetLog("Receiver", "Ack");

        this.expectedSeqNumber = ackPacketParser.getSequenceNumber();
        this.expectedSeqNumber += 1;
    }   

    public TCPSocketImpl createTcpSocket(DatagramPacket dp) {
        String hostAddress = dp.getAddress().getHostAddress();
        int hostPort = dp.getPort();

        TCPSocketImpl tcpSocket = new TCPSocketImpl(socket, hostAddress, hostPort,
                this.sequenceNumber, this.expectedSeqNumber);

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

        sendPacket("Syn/Ack", synAckPacket);
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
