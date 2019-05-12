import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
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

    static final int SENDER_PAYLOAD_LENGTH = 3;

    // If you want to change the TEMP_DATA_SIZE, change the INIT_RWND too
    static final short TEMP_DATA_SIZE = 14;

    enum CongestionState {
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

    private short windowSize;
    private short receiverBufferSize;

    private ArrayList<byte[]> dataBuffer;
    private Queue<byte[]> senderDataBuffer;
    private FileInputStream senderInputStream;
    private ArrayList<Boolean> isReceived;

    public void init(String ip, int port, short base) {
        this.destIp = ip;
        this.destPort = port;

        this.presentState = CongestionState.SLOW_START;
        this.cwnd = INIT_CWND * MSS;
        this.ssthresh = INIT_SSTHRESH * MSS;
        this.receiverBufferSize = INIT_RWND * MSS;
        this.rwnd = receiverBufferSize;
        this.emptyBuffer = receiverBufferSize;

        this.windowSize = (short) Math.min(rwnd, cwnd);
        this.sendBase = base;
        this.nextSeqNumber = base;
    }

    public TCPSocketImpl(EnhancedDatagramSocket socket, String ip, int port, short expectedSeqNumber, short base) {
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

        TCPHeaderParser packetParser = new TCPHeaderParser(packet.getData(), packet.getLength());

        return packetParser;
    }

    public void sendSynPacket() throws Exception {

        TCPHeaderGenerator synPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
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
            ackPacketParser = new TCPHeaderParser(packet.getData(), packet.getLength());

            if (ackPacketParser.isAckPacket() && ackPacketParser.isSynPacket())
                break;
        }

        socket.setSoTimeout(0);
        this.sendBase = ackPacketParser.getAckNumber();
        this.expectedSeqNumber = (short) (ackPacketParser.getSequenceNumber() + 1);
    }

    public void sendAckPacket() throws Exception {
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
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

    public void sendAckPacket(String pathToFile) throws Exception {
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);
        ackPacket.setAckFlag();

        ackPacket.setSequenceNumber(this.nextSeqNumber);
        ackPacket.setAckNumber(this.expectedSeqNumber);
        ackPacket.setRwnd(this.emptyBuffer);

        DatagramPacket sendingPacket = ackPacket.getPacket();
        this.socket.send(sendingPacket);

        System.out.println("## Sending AckNum: " + this.expectedSeqNumber);

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
        this.expectedSeqNumber = (short) Math.max(this.expectedSeqNumber, receivedPacket.getSequenceNumber());

        return receivedPacket;
    }

    public void initializeSocket(Timer time_out_timer) throws Exception {
        this.socket.setLossRate(LOSS_RATE);
        this.socket.setDelayInMilliseconds(DELAY);
        this.sendBase = this.nextSeqNumber;
        this.socket.setSoTimeout(ACK_TIME_OUT);
        this.mostRcvdAck = -1;

        // Start timer
        time_out_timer.schedule(new TimeoutTimer(this), 0, TIME_OUT);
    }

    public void setNewSendBase(Timer timer, short lastRcvdAck) {
        while (this.sendBase < lastRcvdAck) {
            this.sendBase++;
            byte[] temp = this.senderDataBuffer.poll();
            System.out.println(" @@ ACKed: " + new String(temp) + " # sendBase: " + this.sendBase);
        }
        // Restart timer
        timer.cancel();
        timer = new Timer();
        timer.schedule(new TimeoutTimer(this), 0, TIME_OUT);
        this.dupAckCounter = 0;
        this.mostRcvdAck = lastRcvdAck;
    }

    public void updateSenderBuffer() throws IOException {
        while (this.senderDataBuffer.size() < this.windowSize) {
            byte[] temp = new byte[SENDER_PAYLOAD_LENGTH];

            int i = this.senderInputStream.read(temp);
            if (i == -1)
                break;

            this.senderDataBuffer.add(temp);

            if (i < SENDER_PAYLOAD_LENGTH)
                break;
        }
    }

    private byte[] readPayloadFromFile() throws IOException {
        byte[] data = new byte[SENDER_PAYLOAD_LENGTH];

        int i = this.senderInputStream.read(data);
        if (i == -1)
            return null;
        return data;
    }

    public void sendNewWindowPackets() throws Exception {
        while (this.senderDataBuffer.size() < this.windowSize) {
            byte[] data = this.readPayloadFromFile();
            if (data == null)
                break;

            this.senderDataBuffer.add(data);
            TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);

            System.out.println("Window Size: " + this.windowSize + " Sending Data: " + new String(data));

            ackPacket.addData(data);
            sendDataPacket(ackPacket);
        }
    }

    public void dupAckHandler(int lostPacket) throws Exception {
        TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);

        int tempSendBase = this.sendBase;
        Iterator<byte[]> itr = this.senderDataBuffer.iterator();
        byte[] data = null;

        while (itr.hasNext()) {
            data = itr.next();
            if (lostPacket <= tempSendBase)
                break;

            tempSendBase++;
        }

        ackPacket.addData(data);
        ackPacket.setSequenceNumber((short) lostPacket);
        DatagramPacket sendingPacket = ackPacket.getPacket();

        this.socket.send(sendingPacket);

        System.out.println("Sender, retransmit ** : " + new String(data));
    }

    public void slowStartWindowHandler(Timer timer, short lastRcvdAck) {
        this.cwnd += MSS * (lastRcvdAck - this.sendBase);
        this.windowSize = (short) Math.min(rwnd, cwnd);

        System.out.println("New window size: " + this.windowSize);
        setNewSendBase(timer, lastRcvdAck);
    }

    public void fastRecoveryWindowHandler() {
        this.cwnd += MSS;
        this.windowSize = (short) Math.min(rwnd, cwnd);

        System.out.println("New window size: " + this.windowSize);
    }

    public void congestionAvoidanceWindowHandler(Timer timer, short lastRcvdAck) {

        // @TODO: Check it out.
        this.cwnd += (Math.ceil(MSS * (MSS / this.cwnd))) * (lastRcvdAck - this.sendBase);
        this.windowSize = (short) Math.min(rwnd, cwnd);

        System.out.println("New window size: " + this.windowSize);
        setNewSendBase(timer, lastRcvdAck);
    }

    public void fastRecoveryToCongestionAvoidance(Timer timer, short lastRcvdAck) throws Exception {
        this.presentState = CongestionState.CONGESTION_AVOIDANCE;
        this.dupAckCounter = 0;
        this.mostRcvdAck = lastRcvdAck;
        this.cwnd = this.ssthresh;

        System.out.println("New window size: " + this.windowSize);
        setNewSendBase(timer, lastRcvdAck);
    }

    public void changeToFastRecovery(Timer timer, short lastRcvdAck) throws Exception {

        this.presentState = CongestionState.FAST_RECOVERY;

        this.ssthresh = (short) Math.ceil(this.cwnd / 2);
        this.cwnd = (short) (this.ssthresh + 3);

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

    public void processPacket(Timer timer, short lastRcvdAck, short lastRwnd) throws Exception {

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

        System.out.println(
                "## recvd AckNum: " + lastRcvdAck.getAckNumber() + " ##, with the data = " + lastRcvdAck.getData()[0]);

        processPacket(timer, lastRcvdAck.getAckNumber(), lastRcvdAck.getRwnd());
    }

    public void cancelTimers(Timer time_out_timer) {
        time_out_timer.cancel();
    }

    @Override
    public void send(String pathToFile) throws Exception {
        Timer time_out_timer = new Timer();

        initializeSocket(time_out_timer);
        this.senderDataBuffer = new LinkedList<>();
        this.senderInputStream = new FileInputStream(pathToFile);

        while (this.senderInputStream.available() > 0 || this.senderDataBuffer.size() > 0) {
            sendNewWindowPackets();
            getAckPacket(pathToFile, time_out_timer);
        }
        cancelTimers(time_out_timer);

        this.senderInputStream.close();
    }

    // ###################################################
    // ###################################################
    // ###################################################
    // ###################################################
    // ###################################################
    // ###################################################

    public void setRwnd(ArrayList<Boolean> isReceived) {
        short tempRwnd = 0;

        for (int i = 0; i < isReceived.size(); i++)
            if (!isReceived.get(i))
                tempRwnd++;

        this.emptyBuffer = tempRwnd;
    }

    private void popReciverBuffer() {
        this.expectedSeqNumber += 1;
        // @TODO: this.expectedSeqNumber += size of bytes in payload.;
        System.out.println(" @@ saving to file:" + new String(dataBuffer.get(0)));

        // save file in storage.
        this.isReceived.remove(0);
        this.dataBuffer.remove(0);
    }

    public byte addDataToBuffer(TCPHeaderParser packetParser, short windowBaseSeqNum) throws Exception {
        short dataIndex = (short) (packetParser.getSequenceNumber() - this.expectedSeqNumber);

        isReceived.set(dataIndex, true);
        dataBuffer.set(dataIndex, packetParser.getData());
        setRwnd(isReceived);

        if (packetParser.getSequenceNumber() == this.expectedSeqNumber) {
            popReciverBuffer();

            while (this.expectedSeqNumber < isReceived.size() && isReceived.get(this.expectedSeqNumber))
                popReciverBuffer();
        }
        return packetParser.getData()[0];
    }

    public void receiveData(Timer timer, short windowBaseSeqNum) throws Exception {
        TCPHeaderParser packetParser;
        try {
            packetParser = receivePacket();
            restartRwndTimer(timer);
            addDataToBuffer(packetParser, windowBaseSeqNum);
        } catch (Exception e) {
            setRwnd(isReceived);
            return;
        }
    }

    public void resetSenderWindow() throws Exception {
        this.nextSeqNumber = this.sendBase;
        dupAckCounter = 0;
        this.mostRcvdAck = -1;
        this.ssthresh = (short) (Math.ceil((double) this.cwnd / 2));
        this.cwnd = 1 * MSS;
        this.presentState = CongestionState.SLOW_START;

        this.windowSize = (short) Math.min(this.cwnd, this.rwnd);

        System.out.println("## Time-out ## " + "New window size: " + this.windowSize);
        dupAckHandler(this.sendBase);
    }

    public void initializeReceiverSide(Timer timer) throws Exception {
        dataBuffer = new ArrayList<byte[]>(
                Collections.nCopies(this.receiverBufferSize, new byte[this.receiverBufferSize]));

        isReceived = new ArrayList<Boolean>(Arrays.asList(new Boolean[this.receiverBufferSize]));
        Collections.fill(isReceived, Boolean.FALSE);

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
        sendAckPacket("Notifier");
    }

    @Override
    public void receive(String pathToFile) throws Exception {

        Timer rwndTimer = new Timer();
        initializeReceiverSide(rwndTimer);

        short windowBaseSeqNum = this.expectedSeqNumber;

        while (true) {
            if (isReceived.contains(Boolean.FALSE)) {
                receiveData(rwndTimer, windowBaseSeqNum);
                sendAckPacket(pathToFile);
            }
        }
    }

    // ###########################################################
    // ###########################################################
    // ###########################################################
    // ###########################################################
    // ###########################################################

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
