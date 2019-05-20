public class Receiver {
    public static void main(String[] args) throws Exception {
        TCPServerSocket tcpServerSocket = new TCPServerSocketImpl(12345);
        TCPSocket tcpSocket = tcpServerSocket.accept();
        tcpSocket.receive("../src/pat.mp3");
        tcpSocket.close();
        tcpServerSocket.close();
    }
}
