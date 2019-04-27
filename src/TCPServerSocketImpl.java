import java.net.DatagramPacket;
import java.util.Arrays;

public class TCPServerSocketImpl extends TCPServerSocket {
    private EnhancedDatagramSocket socket;
    private int SequenceNum = 300;
    private String destIp;
    private int destPort;

    public TCPServerSocketImpl(int port) throws Exception {
        // the port in which packets are expected to receive
        super(port);
        this.socket = new EnhancedDatagramSocket(port);
    }

    public DatagramPacket getSynPacket() throws Exception {
        byte buf[] = new byte[20];
        DatagramPacket dp = new DatagramPacket(buf, 20);
        TCPHeaderParser dpParser;
        
        while (true) {
            socket.receive(dp);
            dpParser = new TCPHeaderParser(dp.getData());
            if (dpParser.isSynPacket())
                break;
        }

        return dp;
    }

    public TCPHeaderGenerator setupSynAckPacket(DatagramPacket dp) throws Exception {

        TCPHeaderParser dpParser = new TCPHeaderParser(dp.getData());

        short recvSeqNumber = dpParser.getSequenceNumber(); 

        String hostAddress = dp.getAddress().getHostAddress();
        int hostPort = dp.getPort();

        TCPHeaderGenerator synAckPacket = new TCPHeaderGenerator(hostAddress, hostPort);
        synAckPacket.setSynFlag();
        synAckPacket.setAckFlag();
        synAckPacket.setSequenceNumber((short)this.SequenceNum);

        // TODO: decide to whether user +1 or +palyload_size for AckNumber
        synAckPacket.setAckNumber((short)(recvSeqNumber + 1));

        return synAckPacket;
    }

    public void getAckPacket() throws Exception{

        byte ackBuf[] = new byte[20];
        DatagramPacket ackPacket = new DatagramPacket(ackBuf, 20);
        while (true) {
            socket.receive(ackPacket);
            TCPHeaderParser ackPacketParser = new TCPHeaderParser(ackPacket.getData());
            System.out.println(Arrays.toString(ackPacket.getData()));
            if (ackPacketParser.isAckPacket() 
                    && (ackPacketParser.getAckNumber() == this.SequenceNum + 1))
                break;
        }
    }

    public TCPSocketImpl createTcpSocket(DatagramPacket dp) {
        String hostAddress = dp.getAddress().getHostAddress();
        int hostPort = dp.getPort();

        System.out.println("Est");
        TCPSocketImpl tcpSocket = new TCPSocketImpl();
        tcpSocket.init(socket, hostAddress, hostPort);

        return tcpSocket;
    } 
    
    @Override
    public TCPSocket accept() throws Exception {

        // Receive Syn Packet
        DatagramPacket dp = getSynPacket();

        // Send Syn/Ack Packet
        TCPHeaderGenerator synAckPacket = setupSynAckPacket(dp);
        this.socket.send(synAckPacket.getPacket());

        System.out.println("Sent");

        // Receive Ack packet
        getAckPacket();

        return createTcpSocket(dp);
    }

    @Override
    public void close() throws Exception {
        throw new RuntimeException("Not implemented!");
    }
}
