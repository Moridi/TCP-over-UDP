import java.util.TimerTask;

class MyTimerTask extends TimerTask {

    private TCPSocketImpl tcpSocket;


    MyTimerTask ( TCPSocketImpl tcpSocket )
    {
      this.tcpSocket = tcpSocket;
    }

    public void run() {
      this.tcpSocket.resetSenderWindow();
    }
}