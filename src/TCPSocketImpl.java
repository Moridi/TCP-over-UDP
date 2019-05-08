import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import java.util.Timer;

public class TCPSocketImpl extends TCPSocket {
    static final int RCV_TIME_OUT = 1000;
    static final int TIME_OUT = 5000;
    static final int SENDER_PORT = 9090;
    static final short FIRST_SEQUENCE = 100;
    static final short WINDOW_SIZE = 5;
    static final short INIT_CWND = 5;
    static final short INIT_SSTHRESH = 3;
    static final int MIN_BUFFER_SIZE = 80;
    static final int SAMPLE_RTT = 1000;

    enum CongestionState{
        SLOW_START, CONGESTION_AVOIDANCE, FAST_RECOVERY;
    };

    private short cwnd;
    private short ssthresh;
    private CongestionState presentState;

    private EnhancedDatagramSocket socket;
    private short expectedSeqNumber;

    private short sendBase;
    private short nextSeqNumber;

    private String destIp;
    private int destPort;

    private short windowSize;
    private byte tempData[];

    public void init(String ip, int port, short base) {
        this.destIp = ip;
        this.destPort = port;
        this.windowSize = WINDOW_SIZE;

        this.presentState = CongestionState.SLOW_START;
        this.cwnd = INIT_CWND;
        this.ssthresh = INIT_SSTHRESH;
        
        this.sendBase = base;
        this.nextSeqNumber = base;

        this.tempData = new byte[2 * WINDOW_SIZE];
        for (int i = 0; i < this.tempData.length; ++i) {
            tempData[i] = (byte) i;
        }
    }

    public TCPSocketImpl(EnhancedDatagramSocket socket, String ip,
            int port, short expectedSeqNumber, short base) {
        init(ip, port, base);
        this.socket = socket;
        this.expectedSeqNumber = expectedSeqNumber;
    }

    public void packetLog(String type, String name) {
        System.out.println(type + ": " + name +
                ", ExpSeqNum: " + Integer.toString(this.expectedSeqNumber) +
                ", Send base: " + Integer.toString(this.sendBase) +
                ", NextSeq: " + Integer.toString(this.nextSeqNumber));
    }

    public void sendPacket(String name, TCPHeaderGenerator packet)
            throws Exception {
        packet.setSequenceNumber(this.nextSeqNumber);
        packet.setAckNumber(this.expectedSeqNumber);

        DatagramPacket sendingPacket = packet.getPacket();
        this.socket.send(sendingPacket);
        this.nextSeqNumber += 1;

        packetLog("Sender", name);
    }

    public TCPHeaderParser receivePacket() throws Exception {
        byte buffer[] = new byte[MIN_BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, MIN_BUFFER_SIZE);

        socket.receive(packet);

        TCPHeaderParser packetParser = new TCPHeaderParser(
                packet.getData(), packet.getLength());

        return packetParser;
    }

    public void sendSynPacket() throws Exception {

        TCPHeaderGenerator synPacket = new TCPHeaderGenerator(
                this.destIp, this.destPort);
        synPacket.setSynFlag();

        synPacket.setSequenceNumber(this.nextSeqNumber);

        DatagramPacket sendingPacket = synPacket.getPacket();
        this.socket.send(sendingPacket);
        packetLog("Sender", "Syn");

        this.nextSeqNumber += 1;
    }

    public void getSynAckPacket() throws IOException {
        byte bufAck[] = new byte[MIN_BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(bufAck, MIN_BUFFER_SIZE);
        TCPHeaderParser ackPacketParser;

        socket.setSoTimeout(this.RCV_TIME_OUT);

        while (true) {
            socket.receive(packet);
            ackPacketParser = new TCPHeaderParser(packet.getData(),
                    packet.getLength());

            if (ackPacketParser.isAckPacket() && ackPacketParser.isSynPacket())
                break;
        }

        socket.setSoTimeout(0);
        packetLog("Receiver", "Syn/Ack");

        this.sendBase = ackPacketParser.getAckNumber();
        this.expectedSeqNumber = (short)(ackPacketParser.getSequenceNumber() + 1);
    }

    public void sendAckPacket() throws Exception {
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(
                    this.destIp, this.destPort);
        ackPacket.setAckFlag();

        ackPacket.setSequenceNumber(this.nextSeqNumber);
        ackPacket.setAckNumber(this.expectedSeqNumber);

        DatagramPacket sendingPacket = ackPacket.getPacket();
        this.socket.send(sendingPacket);
        packetLog("Sender", "Ack");
        this.nextSeqNumber += 1;
        this.sendBase += 1;
    }

    public TCPSocketImpl(String ip, int port) throws Exception {
        super(ip, port);
        init(ip, port, FIRST_SEQUENCE);
        this.socket = new EnhancedDatagramSocket(SENDER_PORT);

        // TODO: add MAX_RETRY here.
        while (true) {
            try {
                sendSynPacket();
                getSynAckPacket();
                break;
            } catch (SocketTimeoutException ex) {
                this.nextSeqNumber -= 1;
            }
        }
        // TODO: what is ACKPacket gets lost?
        sendAckPacket();
        System.out.println("**** Connection established! ****\n");
    }

    public void sendAckPacket(String pathToFile) throws Exception {
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        ackPacket.setAckFlag();

        ackPacket.setSequenceNumber(this.nextSeqNumber);
        ackPacket.setAckNumber(this.expectedSeqNumber);

        DatagramPacket sendingPacket = ackPacket.getPacket();
        this.socket.send(sendingPacket);

        System.out.println("##\t Sending AckNum: " + this.expectedSeqNumber);

        packetLog("Sender", pathToFile);
        this.nextSeqNumber += 1;
    }

    public void sendDataPacket(TCPHeaderGenerator ackPacket,
            String type, String name) throws Exception {
        ackPacket.setSequenceNumber(this.nextSeqNumber);
        DatagramPacket sendingPacket = ackPacket.getPacket();

        this.socket.send(sendingPacket);
        packetLog(type, name);

        this.nextSeqNumber += 1;
    }

    public short receiveAckPacket(String type, String name) throws Exception {
        TCPHeaderParser receivedPacket;
        while (true) {
            receivedPacket = receivePacket();
            if (receivedPacket.isAckPacket())
                break;
        }
        this.expectedSeqNumber = (short) Math.max(
                this.expectedSeqNumber, receivedPacket.getSequenceNumber());
        System.out.println("## recvd AckNum: " + receivedPacket.getAckNumber() + " ##");

        packetLog(type, name);

        return receivedPacket.getAckNumber();
    }

    public void intializeSocket(Timer timer) {
        packetLog("Sending part", "Start");
        this.socket.setLossRate(0.5);
        this.sendBase = this.nextSeqNumber;
        
        // start timer
        timer.schedule(new MyTimerTask(this), 0, TIME_OUT);
    }

    public void setNewSendBase(Timer timer, short lastRcvdAck) {
        this.sendBase = lastRcvdAck;
        // restart timer
        System.out.println("###### Time-out ######");
        timer.cancel();
        timer = new Timer();
        timer.schedule(new MyTimerTask(this), 0, TIME_OUT);
    }

    public void checkWindowSizeAndSendPacket(short initialSendBase) throws Exception {
        while (this.nextSeqNumber < this.sendBase + this.windowSize) {
            int dataIndex = this.nextSeqNumber - initialSendBase;
            if (dataIndex >= tempData.length)
                break;

            TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(
                    this.destIp, this.destPort);

            ackPacket.addData(tempData[dataIndex]);
            sendDataPacket(ackPacket, "Sender", "** : " + Integer.toString(dataIndex));
        }
        this.socket.setSoTimeout(500);
    }

    public void dupAckHandler(short initialSendBase) throws Exception {
        // reTransmit sendBase
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);

        int dataIndex = this.sendBase - initialSendBase;
        short tempNextSeqNum = this.nextSeqNumber;
        this.nextSeqNumber = this.sendBase;

        ackPacket.addData(tempData[dataIndex]);
        sendDataPacket(ackPacket, "Sender", "retransmit ** : " +
                Integer.toString(dataIndex));

        // nextSeqNumber were increased in sendDataPacket function, so must be decreased:
        this.nextSeqNumber = tempNextSeqNum;
    }

    public int processCommand(Timer timer, short lastRcvdAck,
            int dupAckCounter, short initialSendBase) throws Exception {
        // @TODO: check when (lastRcvdAck == this.sendBase), is it dupACK or not ?!
        if (lastRcvdAck > this.sendBase)
            setNewSendBase(timer, lastRcvdAck);
        else
            dupAckCounter++;

        if (dupAckCounter >= 3) {
            dupAckHandler(initialSendBase);
            dupAckCounter = 0;
        }
        return dupAckCounter;
    }

    @Override
    public void send(String pathToFile) throws Exception {
        Timer timer = new Timer();
        intializeSocket(timer);

        int dupAckCounter = 0;
        short lastRcvdAck;
        short initialSendBase = this.sendBase;

        while (this.sendBase - initialSendBase < tempData.length) {            
            checkWindowSizeAndSendPacket(initialSendBase);

            try {
                lastRcvdAck = receiveAckPacket("Received", pathToFile);
            } catch (SocketTimeoutException ex) {
                continue;
            }

            dupAckCounter = processCommand(timer, lastRcvdAck,
                    dupAckCounter, initialSendBase);
        }
    }

    public void receiveData(Boolean[] isReceived, ArrayList<byte[]>dataBuffer,
            short initialExpectedSeqNum, String type) throws Exception {
        TCPHeaderParser packetParser = receivePacket();

        short dataIndex = (short)(packetParser.getSequenceNumber() - initialExpectedSeqNum);

        isReceived[dataIndex] = true;
        dataBuffer.set(dataIndex, packetParser.getData());
        

        if (packetParser.getSequenceNumber() == this.expectedSeqNumber) {
            this.expectedSeqNumber += 1;
            
            while(isReceived[++dataIndex])
                this.expectedSeqNumber += 1;
            // @TODO: this.expectedSeqNumber += size of bytes in payload.;
        }

        packetLog(type, Integer.toString(dataIndex));
    }

    public void resetSenderWindow() {
        this.nextSeqNumber = this.sendBase;
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        packetLog("Receiving part", "Start");

        ArrayList<byte[]> dataBuffer = new ArrayList<byte[]>(
                Collections.nCopies(100, new byte[100]));
        Boolean[] isReceived = new Boolean[100];
        Arrays.fill(isReceived, false);

        short initialExpectedSeqNum = this.expectedSeqNumber;

        while (true) {
            if (!Arrays.asList(isReceived).contains(false))
                break;

            receiveData(isReceived, dataBuffer, initialExpectedSeqNum, "Received");
            sendAckPacket(pathToFile);
        }
    }

    @Override
    public void close() throws Exception {
        throw new RuntimeException("Not implemented!");
    }

    @Override
    public long getSSThreshold() {
        return ssthresh;
    }

    @Override
    public long getWindowSize() {
        return windowSize;
    }
}
