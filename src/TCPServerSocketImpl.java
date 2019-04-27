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
        short recvSeqNumber;

        byte buf[] = new byte[20];
        DatagramPacket dp = new DatagramPacket(buf, 20);

        TCPHeaderParser dpParser;
        while (true) {
            socket.receive(dp);
            dpParser = new TCPHeaderParser(dp.getData());
            if (dpParser.isSynPacket())
                break;
        }

        // TODO: save sender seq num
        recvSeqNumber = dpParser.getSequenceNumber(); 
        String hostAddress = dp.getAddress().getHostAddress();
        int hostPort = dp.getPort();

        TCPHeaderGenerator synAckPacket = new TCPHeaderGenerator(hostAddress, hostPort);
        synAckPacket.setSynFlag();
        synAckPacket.setAckFlag();
        synAckPacket.setSequenceNumber((short)this.SequenceNum);
        // TODO: decide to whether user +1 or +palyload_size for AckNumber
        synAckPacket.setAckNumber((short)(recvSeqNumber + 1));
        this.socket.send(synAckPacket.getPacket());

        System.out.println("Sent");

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
