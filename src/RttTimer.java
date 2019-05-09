import java.util.TimerTask;

class RttTimer extends TimerTask {

    private TCPSocketImpl tcpSocket;

    RttTimer(TCPSocketImpl tcpSocket) {
      this.tcpSocket = tcpSocket;
    }

    public void run() {
      this.tcpSocket.setNewWindowSize();
    }
}