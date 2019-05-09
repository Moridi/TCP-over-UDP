import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import java.util.Timer;

public class TCPSocketImpl extends TCPSocket {
    static final int RCV_TIME_OUT = 1000;
    static final int TIME_OUT = 8000;
    static final int DELAY = 1000;
    static final int ACK_TIME_OUT = 250;
    static final int MSS = 1;
    static final double LOSS_RATE = 0.2;

    static final int SENDER_PORT = 9090;
    static final short FIRST_SEQUENCE = 100;
    static final short INIT_RWND = 20;
    static final short INIT_CWND = 4;
    static final short INIT_SSTHRESH = 2;
    
    static final int MIN_BUFFER_SIZE = 80;
    static final int SAMPLE_RTT = 1000;

    // If you want to change the TEMP_DATA_SIZE, change the INIT_RWND too
    static final short TEMP_DATA_SIZE = 14;    

    enum CongestionState{
        SLOW_START, CONGESTION_AVOIDANCE, FAST_RECOVERY;
    };

    private short cwnd;

    // It's been used in the sender side
    private short rwnd;

    // It's been used in the receiver side
    private short emptyBuffer;
    
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

    private short initialSendBase;
    private short windowSize;
    private short receiverBufferSize;
    private byte tempData[];

    private ArrayList<byte[]> dataBuffer;
    private Boolean[] isReceived;

    public void init(String ip, int port, short base) {
        this.destIp = ip;
        this.destPort = port;
        
        this.presentState = CongestionState.SLOW_START;
        this.cwnd = INIT_CWND * MSS;
        this.ssthresh = INIT_SSTHRESH * MSS;
        this.receiverBufferSize = INIT_RWND * MSS;
        this.rwnd = receiverBufferSize;
        this.emptyBuffer = receiverBufferSize;
        
        this.windowSize = (short)Math.min(rwnd, cwnd);
        this.sendBase = base;
        this.nextSeqNumber = base;

        this.tempData = new byte[TEMP_DATA_SIZE];
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

    public void sendPacket(TCPHeaderGenerator packet) throws Exception {
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

        socket.setSoTimeout(RCV_TIME_OUT);

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
        ackPacket.setRwnd(this.emptyBuffer);

        // @TODO: Remove it.
        ackPacket.addData(testData);

        DatagramPacket sendingPacket = ackPacket.getPacket();
        this.socket.send(sendingPacket);

        System.out.println("## Sending AckNum: " + this.expectedSeqNumber +
                ", Data: " + testData);
        
        this.nextSeqNumber += 1;
    }

    public void sendDataPacket(TCPHeaderGenerator ackPacket) throws Exception {
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

    public void initializeSocket(Timer time_out_timer) throws Exception {
        this.socket.setLossRate(LOSS_RATE);
        this.socket.setDelayInMilliseconds(DELAY);
        this.sendBase = this.nextSeqNumber;
        this.socket.setSoTimeout(ACK_TIME_OUT);
        this.mostRcvdAck = -1;
        this.initialSendBase = this.sendBase;
        
        // Start timer
        time_out_timer.schedule(new TimeoutTimer(this), 0, TIME_OUT);
    }

    public void setNewSendBase(Timer timer, short lastRcvdAck) {
        this.sendBase = lastRcvdAck;
        // Restart timer
        timer.cancel();
        timer = new Timer();
        timer.schedule(new TimeoutTimer(this), 0, TIME_OUT);
        this.dupAckCounter = 0;
        this.mostRcvdAck = lastRcvdAck;
    }

    public void sendWindowPackets() throws Exception {
        while (this.nextSeqNumber < this.sendBase + this.windowSize) {
            
            int dataIndex = this.nextSeqNumber - this.initialSendBase;
            if (dataIndex >= tempData.length)
                break;
                
            TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(
                    this.destIp, this.destPort);
                    
            System.out.println("Window Size: " + this.windowSize +
                    " Sending Data: " + tempData[dataIndex]);
            ackPacket.addData(tempData[dataIndex]);
            sendDataPacket(ackPacket);
        }
    }

    public void dupAckHandler(int lostPacket) throws Exception {
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        
        int dataIndex = lostPacket - this.initialSendBase;

        if (dataIndex >= tempData.length || dataIndex < 0)
            return;

        ackPacket.addData(tempData[dataIndex]);
        ackPacket.setSequenceNumber((short)lostPacket);
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

    public void congestionAvoidanceWindowHandler(Timer timer, short lastRcvdAck) {

        // @TODO: Check it out.
        this.cwnd += (Math.ceil(MSS * (MSS / this.cwnd))) * (lastRcvdAck - this.sendBase);
        this.windowSize = (short)Math.min(rwnd, cwnd);

        System.out.println("New window size: " + this.windowSize);
        setNewSendBase(timer, lastRcvdAck);
    }

    public void fastRecoveryToCongestionAvoidance(Timer timer, short lastRcvdAck)
            throws Exception {
        this.presentState = CongestionState.CONGESTION_AVOIDANCE;
        this.dupAckCounter = 0;
        this.mostRcvdAck = lastRcvdAck;
        this.cwnd = this.ssthresh;

        System.out.println("New window size: " + this.windowSize);
        setNewSendBase(timer, lastRcvdAck);
    }

    public void changeToFastRecovery(Timer timer, short lastRcvdAck) throws Exception {

        this.presentState = CongestionState.FAST_RECOVERY;
        
        this.ssthresh = (short)Math.ceil(this.cwnd / 2);
        this.cwnd = (short)(this.ssthresh + 3);
        
        dupAckHandler(this.mostRcvdAck);
    }
    
    public void slowStartHandler(Timer timer, short lastRcvdAck) throws Exception {
        
        if (lastRcvdAck > this.sendBase)
            slowStartWindowHandler(timer, lastRcvdAck);
        else if (this.mostRcvdAck == lastRcvdAck || this.mostRcvdAck == -1) {
            this.dupAckCounter++;
            this.mostRcvdAck = lastRcvdAck;
        }
        
        if (this.dupAckCounter >= 3)
            changeToFastRecovery(timer, lastRcvdAck);
        else if (this.cwnd >= this.ssthresh)
            this.presentState = CongestionState.CONGESTION_AVOIDANCE;
        
    }
    
    public void fastRecoveryHandler(Timer timer, short lastRcvdAck) throws Exception {
        if (lastRcvdAck > this.sendBase) {
            fastRecoveryToCongestionAvoidance(timer, lastRcvdAck);
            return;
        }

        fastRecoveryWindowHandler();
    }

    public void congestionAvoidanceHandler(Timer timer, short lastRcvdAck) throws Exception {

        if (lastRcvdAck > this.sendBase)
            congestionAvoidanceWindowHandler(timer, lastRcvdAck);
        else if (this.mostRcvdAck == lastRcvdAck || this.mostRcvdAck == -1) {
            this.dupAckCounter++;
            this.mostRcvdAck = lastRcvdAck;
        }

        if (this.dupAckCounter >= 3)
            changeToFastRecovery(timer, lastRcvdAck);
    }

    public void processPacket(Timer timer, short lastRcvdAck, short lastRwnd)
            throws Exception {

        this.rwnd = lastRwnd;

        switch (this.presentState) {
            case SLOW_START:
                slowStartHandler(timer, lastRcvdAck);
                break;
        
            case FAST_RECOVERY:
                fastRecoveryHandler(timer, lastRcvdAck);
                break;

            case CONGESTION_AVOIDANCE:
                congestionAvoidanceHandler(timer, lastRcvdAck);
                break;
        }

        System.out.println("State: " + this.presentState);
    }

    public void getAckPacket(String pathToFile, Timer timer) throws Exception {
        TCPHeaderParser lastRcvdAck;

        try {
            lastRcvdAck = receiveAckPacket();
        } catch (SocketTimeoutException ex) {
            return;
        }

        System.out.println("## recvd AckNum: " + lastRcvdAck.getAckNumber() +
                " ##, with the data = " + lastRcvdAck.getData()[0]);

        processPacket(timer, lastRcvdAck.getAckNumber(), lastRcvdAck.getRwnd());
    }

    public void cancelTimers(Timer time_out_timer) {
        time_out_timer.cancel();
    }

    @Override
    public void send(String pathToFile) throws Exception {
        Timer time_out_timer = new Timer();

        initializeSocket(time_out_timer);

        while (this.sendBase - this.initialSendBase < tempData.length) {  
            sendWindowPackets();
            getAckPacket(pathToFile, time_out_timer);
        }
        cancelTimers(time_out_timer);
    }

    public void setRwnd(Boolean[] isReceived) {
        short tempRwnd = 0;

        for (int i = 0; i < isReceived.length; i++)
            if (!isReceived[i])
                tempRwnd++;

        this.emptyBuffer = tempRwnd;
    }

    public byte addDataToBuffer(TCPHeaderParser packetParser,
            short initialExpectedSeqNum) throws Exception {
        short dataIndex = (short)(packetParser.getSequenceNumber() - initialExpectedSeqNum);

        isReceived[dataIndex] = true;
        dataBuffer.set(dataIndex, packetParser.getData());
        setRwnd(isReceived);
        
        if (packetParser.getSequenceNumber() == this.expectedSeqNumber) {
            this.expectedSeqNumber += 1;
            
            while(dataIndex < isReceived.length && isReceived[dataIndex]) {
                this.expectedSeqNumber += 1;
                dataIndex++;
            }
            // @TODO: this.expectedSeqNumber += size of bytes in payload.;
        }
        return packetParser.getData()[0];
    }

    public byte receiveData(Timer timer, short initialExpectedSeqNum) throws Exception {
        TCPHeaderParser packetParser;
        try {
            packetParser = receivePacket();
            restartRwndTimer(timer);
            return addDataToBuffer(packetParser, initialExpectedSeqNum);
        } catch (Exception e) {
            setRwnd(isReceived);
            return 0;
        }
    }

    public void resetSenderWindow() throws Exception {
        this.nextSeqNumber = this.sendBase;
        dupAckCounter = 0;
        this.mostRcvdAck = -1;
        this.ssthresh = (short)(Math.ceil((double)this.cwnd / 2));
        this.cwnd = 1 * MSS;
        this.presentState = CongestionState.SLOW_START;

        this.windowSize = (short)Math.min(this.cwnd, this.rwnd);
        
        System.out.println("## Time-out ## " + "New window size: " + this.windowSize);
        dupAckHandler(this.sendBase);
    }

    public void initializeReceiverSide(Timer timer) throws Exception {
        dataBuffer = new ArrayList<byte[]>(Collections.nCopies(this.receiverBufferSize,
                new byte[this.receiverBufferSize]));
        isReceived = new Boolean[this.receiverBufferSize];
        Arrays.fill(isReceived, false);

        // Start timer
        timer.schedule(new RwndNotifier(this), 0, TIME_OUT);
    }
    
    public void restartRwndTimer(Timer timer) {
        // Restart timer
        timer.cancel();
        timer = new Timer();
        timer.schedule(new RwndNotifier(this), 0, TIME_OUT);
    }

    // It's just for notifying the sender when the buffer gets empty
    public void sendSimpleAck() throws Exception {
        System.out.println("Send notifier to the sender");
        sendAckPacket("Notifier", (byte)0);
    }

    @Override
    public void receive(String pathToFile) throws Exception {

        Timer rwndTimer = new Timer();
        initializeReceiverSide(rwndTimer);
        Byte testData = 0;

        short initialExpectedSeqNum = this.expectedSeqNumber;

        while (true) {
            if (Arrays.asList(isReceived).contains(false)) {
                testData = receiveData(rwndTimer, initialExpectedSeqNum);
                sendAckPacket(pathToFile, testData);
            }
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
