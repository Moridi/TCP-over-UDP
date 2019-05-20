import java.io.FileInputStream;
import java.io.FileOutputStream;
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
    static final int TIME_OUT = 650;
    static final int DELAY = 100;
    static final int MSS = 1;
    static final double LOSS_RATE = 0;

    static final int SENDER_PORT = 9090;
    static final short FIRST_SEQUENCE = 100;
    static final short INIT_RWND = 200;
    static final short INIT_CWND = 1;
    static final short INIT_SSTHRESH = 100;

    static final int MIN_BUFFER_SIZE = EnhancedDatagramSocket.DEFAULT_PAYLOAD_LIMIT_IN_BYTES;
    static final int SAMPLE_RTT = 10000;

    static final int SENDER_PAYLOAD_LENGTH = EnhancedDatagramSocket.DEFAULT_PAYLOAD_LIMIT_IN_BYTES
            - TCPHeaderGenerator.MIN_HEADER_SIZE;

    static final int MAX_PAYLOAD_LENGTH = EnhancedDatagramSocket.DEFAULT_PAYLOAD_LIMIT_IN_BYTES;

    enum CongestionState {
        SLOW_START, CONGESTION_AVOIDANCE, FAST_RECOVERY;
    };

    private float cwnd;

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

    private float windowSize;
    private short receiverBufferSize;

    private ArrayList<byte[]> dataBuffer;
    private Queue<byte[]> senderDataBuffer;
    private FileInputStream senderInputStream;
    private FileOutputStream recieverOutputStream;
    private ArrayList<Boolean> isReceived;

    private Timer timer;

    public void init(String ip, int port, short base) {
        this.destIp = ip;
        this.destPort = port;

        this.presentState = CongestionState.SLOW_START;
        this.cwnd = INIT_CWND * MSS;
        this.setSSThreshold((short) (INIT_SSTHRESH * MSS));
        this.receiverBufferSize = INIT_RWND * MSS;
        this.rwnd = receiverBufferSize;
        this.emptyBuffer = receiverBufferSize;

        setWindowSize();
        this.setSendBase(base);
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

        socket.setSoTimeout(TIME_OUT);

        while (true) {
            socket.receive(packet);
            ackPacketParser = new TCPHeaderParser(packet.getData(), packet.getLength());

            if (ackPacketParser.isAckPacket() && ackPacketParser.isSynPacket())
                break;
        }

        socket.setSoTimeout(0);
        this.setSendBase(ackPacketParser.getAckNumber());
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

        while (true) {
            try {
                sendSynPacket();
                getSynAckPacket();
                break;
            } catch (SocketTimeoutException ex) {
                this.nextSeqNumber -= 1;
            }
        }
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

        System.out.println("Sending AckNum: " + this.expectedSeqNumber);

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

    public void initializeSocket() throws Exception {
        this.socket.setLossRate(LOSS_RATE);
        this.socket.setDelayInMilliseconds(DELAY);
        this.setSendBase(this.nextSeqNumber);
        this.socket.setSoTimeout(TIME_OUT);
        this.mostRcvdAck = -1;

        // Start timer
        this.timer.scheduleAtFixedRate(new TimeoutTimer(this), 0, TIME_OUT);
    }

    public void setNewSendBase(short lastRcvdAck) {
        while (this.sendBase < lastRcvdAck) {
            this.sendBase++;
            byte[] temp = this.senderDataBuffer.poll();
            System.out.println(" @@ ACKed # sendBase: " + this.sendBase);
        }
        if (this.nextSeqNumber < this.sendBase)
            this.nextSeqNumber = this.sendBase;
        // Restart timer
        this.timer.cancel();
        this.timer = new Timer();
        this.timer.scheduleAtFixedRate(new TimeoutTimer(this), 0, TIME_OUT);
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

        return Arrays.copyOf(data, i);
    }

    public void sendNewWindowPackets() throws Exception {
        while (this.senderDataBuffer.size() < this.windowSize) {
            byte[] data = this.readPayloadFromFile();
            if (data == null)
                break;

            this.senderDataBuffer.add(data);
            TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);

            System.out.println("Window Size: " + this.windowSize + " SeqNum: " + this.nextSeqNumber);

            ackPacket.addData(data);
            sendDataPacket(ackPacket);
        }
    }

    public void sendWindowPackets() throws Exception {
        short temSeqNum = this.nextSeqNumber;
        this.nextSeqNumber = this.sendBase;

        Iterator<byte[]> itr = this.senderDataBuffer.iterator();
        for (int i = 0; i < this.windowSize; i++) {
            if (!itr.hasNext())
                break;
            byte[] data = itr.next();

            TCPHeaderGenerator ackPacket = new TCPHeaderGenerator(this.destIp, this.destPort);

            System.out.println("Window Size: " + this.windowSize + " SeqNum: " + this.nextSeqNumber);

            ackPacket.addData(data);
            sendDataPacket(ackPacket);
        }

        this.nextSeqNumber = temSeqNum;
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
        this.dupAckCounter = 0;

        System.out.println("Sender, retransmit ** SeqNum: " + lostPacket);
    }

    public void slowStartWindowHandler(short lastRcvdAck) {
        this.cwnd += MSS * (lastRcvdAck - this.sendBase);
        setWindowSize();

        System.out.println("New window size: " + this.windowSize);
        setNewSendBase(lastRcvdAck);
    }

    public void fastRecoveryWindowHandler() {
        this.cwnd += MSS;
        setWindowSize();

        System.out.println("New window size: " + this.windowSize);
    }

    public void congestionAvoidanceWindowHandler(short lastRcvdAck) {

        this.cwnd += (MSS * ((float) MSS / this.cwnd) * (lastRcvdAck - this.sendBase));
        setWindowSize();

        System.out.println("New window size: " + this.windowSize);
        setNewSendBase(lastRcvdAck);
    }

    public void fastRecoveryToCongestionAvoidance(short lastRcvdAck) throws Exception {
        this.presentState = CongestionState.CONGESTION_AVOIDANCE;
        this.dupAckCounter = 0;
        this.mostRcvdAck = lastRcvdAck;
        this.cwnd = this.ssthresh;
        setWindowSize();

        System.out.println("New window size: " + this.windowSize);
        setNewSendBase(lastRcvdAck);
    }

    public void changeToFastRecovery(short lastRcvdAck) throws Exception {

        this.presentState = CongestionState.FAST_RECOVERY;

        this.setSSThreshold((short) Math.ceil(this.cwnd / 2));
        this.cwnd = (short) (this.ssthresh + 3);
        setWindowSize();

        dupAckHandler(this.mostRcvdAck);
    }

    public void slowStartHandler(short lastRcvdAck) throws Exception {

        if (lastRcvdAck > this.sendBase)
            slowStartWindowHandler(lastRcvdAck);
        else if (this.mostRcvdAck == lastRcvdAck || this.mostRcvdAck == -1) {
            this.dupAckCounter++;
            this.mostRcvdAck = lastRcvdAck;
        }

        if (this.dupAckCounter >= 3)
            changeToFastRecovery(lastRcvdAck);
        else if (this.cwnd >= this.ssthresh)
            this.presentState = CongestionState.CONGESTION_AVOIDANCE;

    }

    public void fastRecoveryHandler(short lastRcvdAck) throws Exception {
        if (lastRcvdAck > this.sendBase) {
            fastRecoveryToCongestionAvoidance(lastRcvdAck);
            return;
        }

        fastRecoveryWindowHandler();
        dupAckCounter++;
        if (dupAckCounter >= 3) {
            dupAckHandler(lastRcvdAck);
        }
    }

    public void congestionAvoidanceHandler(short lastRcvdAck) throws Exception {

        if (lastRcvdAck > this.sendBase)
            congestionAvoidanceWindowHandler(lastRcvdAck);
        else if (this.mostRcvdAck == lastRcvdAck || this.mostRcvdAck == -1) {
            this.dupAckCounter++;
            this.mostRcvdAck = lastRcvdAck;
        }

        if (this.dupAckCounter >= 3)
            changeToFastRecovery(lastRcvdAck);
    }

    public void processPacket(short lastRcvdAck, short lastRwnd) throws Exception {

        this.rwnd = lastRwnd;

        switch (this.presentState) {
        case SLOW_START:
            slowStartHandler(lastRcvdAck);
            break;

        case FAST_RECOVERY:
            fastRecoveryHandler(lastRcvdAck);
            break;

        case CONGESTION_AVOIDANCE:
            congestionAvoidanceHandler(lastRcvdAck);
            break;
        }

        System.out.println("State: " + this.presentState);
    }

    public void getAckPacket(String pathToFile) throws Exception {
        TCPHeaderParser lastRcvdAck;

        try {
            lastRcvdAck = receiveAckPacket();
        } catch (SocketTimeoutException ex) {
            return;
        }

        System.out.println("## recvd AckNum: " + lastRcvdAck.getAckNumber() + " ##");

        processPacket(lastRcvdAck.getAckNumber(), lastRcvdAck.getRwnd());
    }

    public void cancelTimers() {
        this.timer.cancel();
    }

    @Override
    public void send(String pathToFile) throws Exception {
        this.timer = new Timer();

        initializeSocket();
        this.senderDataBuffer = new LinkedList<>();
        this.senderInputStream = new FileInputStream(pathToFile);

        while (this.senderInputStream.available() > 0 || this.senderDataBuffer.size() > 0) {
            sendNewWindowPackets();
            getAckPacket(pathToFile);
        }
        cancelTimers();
        this.senderInputStream.close();
        this.close();
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

    private void popReciverBuffer() throws IOException {
        this.expectedSeqNumber += 1;
        System.out.println(" @@ saving to file");

        this.recieverOutputStream.write(dataBuffer.get(0));

        this.isReceived.remove(0);
        this.dataBuffer.remove(0);

        this.isReceived.add(false);
        this.dataBuffer.add(new byte[MAX_PAYLOAD_LENGTH]);
    }

    public void addDataToBuffer(TCPHeaderParser packetParser, short windowBaseSeqNum) throws Exception {
        short dataIndex = (short) (packetParser.getSequenceNumber() - this.expectedSeqNumber);

        if (isReceived.get(dataIndex))
            return;

        isReceived.set(dataIndex, true);
        dataBuffer.set(dataIndex, packetParser.getData());

        System.out.println(" ## saved to buffer, AckNum: " + packetParser.getSequenceNumber());
        setRwnd(isReceived);

        if (packetParser.getSequenceNumber() == this.expectedSeqNumber) {
            popReciverBuffer();

            while (isReceived.size() > 0 && isReceived.get(0))
                popReciverBuffer();
        }
        return;
    }

    public void receiveData(short windowBaseSeqNum) throws Exception {
        TCPHeaderParser packetParser;
        try {
            packetParser = receivePacket();
            restartRwndTimer();
            addDataToBuffer(packetParser, windowBaseSeqNum);
        } catch (Exception e) {
            setRwnd(isReceived);
            return;
        }
    }

    public void resetSenderWindow() throws Exception {
        this.setSSThreshold((short) (Math.ceil((double) this.cwnd / 2)));
        this.cwnd = 1 * MSS;
        this.presentState = CongestionState.SLOW_START;
        setWindowSize();

        System.out.println("## Time-out ## " + "New window size: " + this.windowSize);
        // sendWindowPackets();
        dupAckHandler(this.sendBase);
    }

    public void initializeReceiverSide() throws Exception {
        dataBuffer = new ArrayList<byte[]>(Collections.nCopies(this.receiverBufferSize, new byte[MAX_PAYLOAD_LENGTH]));

        isReceived = new ArrayList<Boolean>(Arrays.asList(new Boolean[this.receiverBufferSize]));
        Collections.fill(isReceived, Boolean.FALSE);

        // Start timer
        this.timer = new Timer();
        this.timer.scheduleAtFixedRate(new RwndNotifier(this), 0, TIME_OUT);
    }

    public void restartRwndTimer() {
        // Restart timer
        this.timer.cancel();
        this.timer = new Timer();
        this.timer.scheduleAtFixedRate(new RwndNotifier(this), 0, TIME_OUT);
    }

    // It's just for notifying the sender when the buffer gets empty
    public void sendSimpleAck() throws Exception {
        System.out.println("Send notifier to the sender");
        sendAckPacket("Notifier");
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        this.recieverOutputStream = new FileOutputStream(pathToFile);

        initializeReceiverSide();

        short windowBaseSeqNum = this.expectedSeqNumber;

        while (true) {
            if (isReceived.contains(Boolean.FALSE)) {
                receiveData(windowBaseSeqNum);
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
        this.socket.close();
    }

    @Override
    public float getSSThreshold() {
        return ssthresh;
    }

    @Override
    public float getWindowSize() {
        return windowSize;
    }

    public void setWindowSize() {
        float value = Math.min(rwnd, cwnd);

        this.windowSize = value;
        this.onWindowChange();

    }

    private void setSendBase(short value) {
        this.sendBase = value;
    }

    private void setSSThreshold(short value) {
        this.ssthresh = value;
        this.onWindowChange();
    }
}
