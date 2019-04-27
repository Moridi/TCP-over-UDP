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

    @Override
    public TCPSocket accept() throws Exception {
        byte buf[] = new byte[2];
        DatagramPacket dp = new DatagramPacket(buf, 2);

        while (true) {
            socket.receive(dp);
            TCPHeaderParser dpParser = new TCPHeaderParser(dp.getData());
            if (dpParser.isAckPacket() && dpParser.isSynPacket())
                break;
        }

        System.out.println(dp.getData());

        String hostAddress = dp.getAddress().getHostAddress();
        int hostPort = dp.getPort();

        TCPHeaderGenerator synAckPacket = new TCPHeaderGenerator(hostAddress, hostPort);
        synAckPacket.setSynFlag();
        synAckPacket.setAckFlag();
        // TODO: set sequence number
        this.socket.send(synAckPacket.getPacket());

        System.out.println("Sent");

        byte ackBuf[] = new byte[2];
        DatagramPacket ackPacket = new DatagramPacket(ackBuf, 2);
        while (true) {
            socket.receive(ackPacket);
            TCPHeaderParser ackPacketParser = new TCPHeaderParser(ackPacket.getData());
            if (ackPacketParser.isAckPacket() && ackPacketParser.isSynPacket())
                break;
        }

        System.out.println("Est");
        TCPSocketImpl tcpSocket = new TCPSocketImpl();
        tcpSocket.init(socket, hostAddress, hostPort);
        return tcpSocket;
    }

    @Override
    public void close() throws Exception {
        throw new RuntimeException("Not implemented!");
    }
}
