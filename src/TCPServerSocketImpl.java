import java.net.DatagramPacket;
import java.util.Arrays;

public class TCPServerSocketImpl extends TCPServerSocket {
    private EnhancedDatagramSocket socket;

    public TCPServerSocketImpl(int port) throws Exception {
        // the port in which packets are expected to receive
        super(port);
        this.socket = new EnhancedDatagramSocket(port);


        byte buf[] = new byte[2];
        DatagramPacket dp = new DatagramPacket(buf, 2);

        socket.receive(dp);

        System.out.println(Arrays.toString(dp.getData()));
    }

    @Override
    // public TCPSocket accept() throws Exception {
    public TCPSocket accept() throws Exception {
        // if got an ack, accept and do handshake
        // else, wait!
        if (false) {
            throw new RuntimeException("Not implemented!");
        }
        return new TCPSocketImpl("127.0.0.1", 2134);
    }

    @Override
    public void close() throws Exception {
        throw new RuntimeException("Not implemented!");
    }
}
