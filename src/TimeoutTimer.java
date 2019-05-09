import java.util.TimerTask;

class TimeoutTimer extends TimerTask {

    private TCPSocketImpl tcpSocket;

    TimeoutTimer ( TCPSocketImpl tcpSocket ) {
      this.tcpSocket = tcpSocket;
    }

    public void run() {
      this.tcpSocket.resetSenderWindow();
    }
}