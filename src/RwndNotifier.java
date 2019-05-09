import java.util.TimerTask;

class RwndNotifier extends TimerTask {

    private boolean timeOut;
    private TCPSocketImpl tcpSocket;

    RwndNotifier(TCPSocketImpl tcpSocket) {
      this.timeOut = false;
      this.tcpSocket = tcpSocket;
    }

    public void run() {
      if (this.timeOut)
        try {
            this.tcpSocket.sendSimpleAck();
        } catch (Exception e) {
            return;
        }

      this.timeOut = true;
    }
}