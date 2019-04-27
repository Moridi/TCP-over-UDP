import java.net.DatagramPacket;
import java.net.InetAddress;

public class TCPSocketImpl extends TCPSocket {
    static final int senderPort = 9090;
    private EnhancedDatagramSocket socket;

    public TCPSocketImpl(String ip, int port) throws Exception {
        // destination's ip and port
        super(ip, port);
        this.socket = new EnhancedDatagramSocket(senderPort);

        InetAddress address = InetAddress.getByName("localhost");

        byte buf[] = { 11, 12 };
        DatagramPacket dp = new DatagramPacket(buf, 2, address, port);
        this.socket.send(dp);

        System.out.println("Sent");
        // should first do a handshake!
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
