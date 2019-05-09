import java.util.TimerTask;

class TimeoutTimer extends TimerTask {

    private boolean timeOut;
    private TCPSocketImpl tcpSocket;

    TimeoutTimer ( TCPSocketImpl tcpSocket ) {
      this.timeOut = false;
      this.tcpSocket = tcpSocket;
    }

    public void run() {
      if (this.timeOut)
        this.tcpSocket.resetSenderWindow();

      this.timeOut = true;
    }
}