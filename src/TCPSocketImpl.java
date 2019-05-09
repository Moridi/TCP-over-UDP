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
    static final int DELAY = 1000;
    static final int ACK_TIME_OUT = 250;
    static final int MSS = 1;
    static final double LOSS_RATE = 0.2;

    static final int SENDER_PORT = 9090;
    static final short FIRST_SEQUENCE = 100;
    static final short INIT_RWND = 100;
    static final short INIT_CWND = 4;
    static final short INIT_SSTHRESH = 2;
    
    static final int MIN_BUFFER_SIZE = 80;
    static final int SAMPLE_RTT = 1000;

    enum CongestionState{
        SLOW_START, CONGESTION_AVOIDANCE, FAST_RECOVERY;
    };

    private short cwnd;
    private short rwnd;
    private short ssthresh;
    private CongestionState presentState;
    private int dupAckCounter;
    private short mostRcvdAck;

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
        
        this.presentState = CongestionState.SLOW_START;
        this.cwnd = INIT_CWND * MSS;
        this.ssthresh = INIT_SSTHRESH * MSS;
        this.rwnd = INIT_RWND * MSS;
        
        this.windowSize = (short)Math.min(rwnd, cwnd);
        this.sendBase = base;
        this.nextSeqNumber = base;

        this.tempData = new byte[14];
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

    public void sendPacket(String name, TCPHeaderGenerator packet)
            throws Exception {
        packet.setSequenceNumber(this.nextSeqNumber);
        packet.setAckNumber(this.expectedSeqNumber);

        DatagramPacket sendingPacket = packet.getPacket();
        this.socket.send(sendingPacket);
        this.nextSeqNumber += 1;
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

    public void sendAckPacket(String pathToFile, Byte testData) throws Exception {
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        ackPacket.setAckFlag();

        ackPacket.setSequenceNumber(this.nextSeqNumber);
        ackPacket.setAckNumber(this.expectedSeqNumber);

        // @TODO: Remove it.
        ackPacket.addData(testData);

        DatagramPacket sendingPacket = ackPacket.getPacket();
        this.socket.send(sendingPacket);

        System.out.println("## Sending AckNum: " + this.expectedSeqNumber
                + ", Data: " + testData);
        
        this.nextSeqNumber += 1;
    }

    public void sendDataPacket(TCPHeaderGenerator ackPacket,
            String type, String name) throws Exception {
        ackPacket.setSequenceNumber(this.nextSeqNumber);
        DatagramPacket sendingPacket = ackPacket.getPacket();

        this.socket.send(sendingPacket);
        this.nextSeqNumber += 1;
    }

    public TCPHeaderParser receiveAckPacket() throws Exception {
        TCPHeaderParser receivedPacket;
        while (true) {
            receivedPacket = receivePacket();
            if (receivedPacket.isAckPacket())
                break;
        }
        this.expectedSeqNumber = (short)Math.max(this.expectedSeqNumber,
                receivedPacket.getSequenceNumber());

        return receivedPacket;
    }

    public void intializeSocket(Timer time_out_timer) throws Exception {
        this.socket.setLossRate(LOSS_RATE);
        this.socket.setDelayInMilliseconds(DELAY);
        this.sendBase = this.nextSeqNumber;
        this.socket.setSoTimeout(ACK_TIME_OUT);
        this.mostRcvdAck = -1;
        
        // start timer
        time_out_timer.schedule(new TimeoutTimer(this), 0, TIME_OUT);
    }

    public void setNewSendBase(Timer timer, short lastRcvdAck) {
        this.sendBase = lastRcvdAck;
        // restart timer
        timer.cancel();
        timer = new Timer();
        timer.schedule(new TimeoutTimer(this), 0, TIME_OUT);
        this.dupAckCounter = 0;
        this.mostRcvdAck = lastRcvdAck;
    }

    public void checkWindowSizeAndSendPacket(short initialSendBase) throws Exception {
        while (this.nextSeqNumber < this.sendBase + this.windowSize) {
            
            int dataIndex = this.nextSeqNumber - initialSendBase;
            if (dataIndex >= tempData.length)
                break;
                
                TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(
                    this.destIp, this.destPort);
                    
            System.out.println("Window Size: " + this.windowSize +
                    " Sending Data: " + tempData[dataIndex]);
            ackPacket.addData(tempData[dataIndex]);
            sendDataPacket(ackPacket, "Sender", "** : " + Integer.toString(dataIndex));
        }
    }

    public void dupAckHandler(short initialSendBase) throws Exception {
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        
        int dataIndex = this.mostRcvdAck - initialSendBase;
        ackPacket.addData(tempData[dataIndex]);

        ackPacket.setSequenceNumber(this.mostRcvdAck);
        DatagramPacket sendingPacket = ackPacket.getPacket();

        this.socket.send(sendingPacket);

        System.out.println("Sender, retransmit ** : " +
                Integer.toString(tempData[dataIndex]));
    }

    public void slowStartWindowHandler(Timer timer, short lastRcvdAck) {
        this.cwnd += MSS * (lastRcvdAck - this.sendBase);
        this.windowSize = (short)Math.min(rwnd, cwnd);

        System.out.println("New window size: " + this.windowSize);
        setNewSendBase(timer, lastRcvdAck);
    }

    public void fastRecoveryWindowHandler() {
        this.cwnd += MSS;
        this.windowSize = (short)Math.min(rwnd, cwnd);

        System.out.println("New window size: " + this.windowSize);
    }

    public void fastRecoveryHandler(Timer timer, short lastRcvdAck,
            short initialSendBase) throws Exception {
        if (lastRcvdAck > this.sendBase) {
            this.presentState = CongestionState.SLOW_START;
            this.dupAckCounter = 0;
            this.mostRcvdAck = lastRcvdAck;
            // @TODO: Chagne it to Congestion Avoidance
            slowStartHandler(timer, lastRcvdAck, initialSendBase);
            return;
        }

        fastRecoveryWindowHandler();
    }
    
    public void slowStartToFastRecovery(Timer timer, short lastRcvdAck,
            short initialSendBase) throws Exception {

        this.presentState = CongestionState.FAST_RECOVERY;
        
        this.ssthresh = (short)Math.ceil(this.cwnd / 2);
        this.cwnd = (short)(this.ssthresh + 3);

        dupAckHandler(initialSendBase);
    }

    public void slowStartHandler(Timer timer, short lastRcvdAck,
            short initialSendBase) throws Exception {

        if (lastRcvdAck > this.sendBase)
            slowStartWindowHandler(timer, lastRcvdAck);
        else if (this.mostRcvdAck == lastRcvdAck || this.mostRcvdAck == -1) {
            this.dupAckCounter++;
            this.mostRcvdAck = lastRcvdAck;
        }

        if (this.dupAckCounter >= 3)
            slowStartToFastRecovery(timer, lastRcvdAck, initialSendBase);
    }

    public void processPacket(Timer timer, short lastRcvdAck,
            short initialSendBase) throws Exception {
        // @TODO: check when (lastRcvdAck == this.sendBase), is it dupACK or not ?!

        System.out.println("presentState: " + this.presentState);
        switch (this.presentState) {
            case SLOW_START:
                slowStartHandler(timer, lastRcvdAck, initialSendBase);
                break;
        
            case FAST_RECOVERY:
                fastRecoveryHandler(timer, lastRcvdAck, initialSendBase);
                break;
            default:
                break;
        }
    }

    public void getAckPacket(String pathToFile, Timer timer,
            short initialSendBase) throws Exception {
        TCPHeaderParser lastRcvdAck;

        try {
            lastRcvdAck = receiveAckPacket();
        } catch (SocketTimeoutException ex) {
            return;
        }

        System.out.println("## recvd AckNum: " + lastRcvdAck.getAckNumber() +
                " ##, with the data = " + lastRcvdAck.getData()[0]);
        
        processPacket(timer, lastRcvdAck.getAckNumber(), initialSendBase);
    }

    public void cancelTimers(Timer time_out_timer) {
        time_out_timer.cancel();
    }

    @Override
    public void send(String pathToFile) throws Exception {
        Timer time_out_timer = new Timer();

        intializeSocket(time_out_timer);

        short initialSendBase = this.sendBase;

        while (this.sendBase - initialSendBase < tempData.length) {  
            checkWindowSizeAndSendPacket(initialSendBase);
            getAckPacket(pathToFile, time_out_timer, initialSendBase);
        }
        cancelTimers(time_out_timer);
    }

    public byte receiveData(Boolean[] isReceived, ArrayList<byte[]>dataBuffer,
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

        return packetParser.getData()[0];
    }

    public void resetSenderWindow() {
        this.nextSeqNumber = this.sendBase;
        dupAckCounter = 0;
        this.mostRcvdAck = -1;
        this.ssthresh = (short)(Math.ceil((double)this.cwnd / 2));
        this.cwnd = 1 * MSS;
        this.presentState = CongestionState.SLOW_START;
        
        this.windowSize = (short)Math.min(this.cwnd, this.rwnd);

        System.out.println("## Time-out ## " + "New window size: " + this.windowSize);
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        ArrayList<byte[]> dataBuffer = new ArrayList<byte[]>(
                Collections.nCopies(100, new byte[100]));
        Boolean[] isReceived = new Boolean[100];
        Arrays.fill(isReceived, false);

        short initialExpectedSeqNum = this.expectedSeqNumber;

        while (true) {
            if (!Arrays.asList(isReceived).contains(false))
                break;

            Byte testData = receiveData(isReceived,
                    dataBuffer, initialExpectedSeqNum, "Received");
            sendAckPacket(pathToFile, testData);
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
