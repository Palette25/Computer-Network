package lftp.Client;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Queue;
import lftp.Server.UDPServer;
import lftp.Server.UDPPacket;

public class UDPClient{
	// Client's private members
	private final int PORT_NUM = 8082;
	private final int MAX_LENGTH = 50 * 1024;  // Max bytes length for one packet
	private DatagramSocket socket;
	private InetAddress serverHost;  // Target server host
	private int serverPort = 8080;  // Target server port num, default to 8080
	private long swnd = 500;  // Send window size, make flow control
	private long rwnd = 500;  // Receive window size, make flow control
	private int cwnd = 1;  // Client side's congestion window size
	private int ssthresh = 30;  // Client side's congestion threads hold
	private long buffSize;  // Record client receive buffer empty space
	private String clientDir = "lftp/Files/ClientFiles/";

	private int seq;
	private int syn;
	private int fin;
	private Queue<UDPPacket> packetQueue;  // Current sending packets buffer
	private boolean status;  // Client's state, false for free, true for busy
	private ProcessBar bar;  // The process bar of file transfering

	// Startup constructed method
	public UDPClient(){
		try {
			socket = new DatagramSocket(PORT_NUM);
			// Default server host -- localhost
			serverHost = InetAddress.getByName("127.0.0.1");
			packetQueue = new LinkedList<>();
		} catch(SocketException e) {
			e.printStackTrace();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	public void handleSendRequest(String path, String serverPath) throws IOException{
		System.out.printf("[Info] Ready to send file : %s\n", path);
		// Check path validation
		File file = new File(clientDir + path);
		// Exist check
		if(!file.exists()){
			System.out.printf("[Error] %s does not exist! Please check the path of the file you want to send!\n", path);
			return;
		} else if(!file.isFile()){
			// File or folder check
			System.out.printf("[Error] %s is not a file!\n", path);
			return;
		}
		// File readability check
		try{
			InputStream stream = new FileInputStream(file);
		} catch(Exception e){
			System.out.printf("[Error] Cannot read file: %s!\n", path);
			return;
		}
		// Shake hands with target server, and then begin to send files
		if(!ShakeHandsWithServer(serverPath)){
			return;
		}
		// Init receive buffer
		this.buffSize = file.length() / MAX_LENGTH + 1;
		this.swnd = this.buffSize / 2;
		// Begin sendind file, send begin flag(Feature)
		UDPPacket packet0 = new UDPPacket(this.seq++);
		String begin = "BEGIN " + path;
		packet0.setBytes(begin.getBytes());
		// Make packet checksum
		packet0.calculateSum();
		// Make window size deliver to server
		packet0.setWinSize(this.swnd);
		this.packetQueue.add(packet0);
		bar = new ProcessBar(this.seq-1, file.length(), path);
		bar.initBar(true);
		// Start timer
		long start = System.currentTimeMillis();
		// Send file by dividing into pieces
		RandomAccessFile accessFile = new RandomAccessFile(clientDir + path, "r");
		byte[] buffer = new byte[MAX_LENGTH];
		int readPos = -1;
		while((readPos = accessFile.read(buffer, 0, buffer.length)) != -1){
			UDPPacket packet = new UDPPacket(this.seq);
			this.seq++;
			packet.setBytes(buffer);
			packet.calculateSum();
			this.packetQueue.add(packet);
			// Send file block one by one
			if(this.status == false)
				sendPacket();
		}
		accessFile.close();
		// End file sending, send success flag(Feature)
		UDPPacket packet1 = new UDPPacket(this.seq++);
		String end = "SUCCESS " + path;
		packet1.setBytes(end.getBytes());
		packet1.calculateSum();
		this.packetQueue.add(packet1);
		sendPacket();
		// Calculate cost time
		long end1 = System.currentTimeMillis();
		double costTime = (end1 - start) / 1000.0f;
		// Calculate size in MB unit
		double size = file.length() / (1024.0f * 1024.0f);
		System.out.printf("\n[Info]LFTP-Client successfully sends large file: %s, file Size: %.2f MB, send Time: %.2f sec\n", path, size, costTime);
		// End connection with target server
		WaveHandsWithServer();
	}

	public void handleGetRequest(String path, String serverPath) throws IOException{
		// Init shake hands with target server
		ShakeHandsWithServer(serverPath);
		// Send server a download request
		UDPPacket getRequestPacket = new UDPPacket(this.seq);
		this.seq++;
		String str = "GET " + path;
		getRequestPacket.setBytes(str.getBytes());
		while(true){
			byte[] byteData = getRequestPacket.getPacketBytes();
			DatagramPacket outPacket = new DatagramPacket(byteData, byteData.length, serverHost, serverPort);
			socket.send(outPacket);
			// Check return ack or error packet
			UDPPacket backPacket;
			while((backPacket = receivePacket()) == null){}
			String mess = new String(backPacket.getBytes());
			String[] res = mess.split(" ");
			if(res.length > 1 && res[0].equals("ERROR")){
				System.out.printf("[Error] Download %s failed, target file does not exist!\n", res[1]);
				return;
			}else if(res.length > 1 && res[0].equals("OK")){
				int errTime = 0;
				if(backPacket != null && backPacket.isACK() && backPacket.checkSum()){
					this.rwnd = backPacket.getWinSize();
					startDownload(path, res[1]);
					break;
				}else {
					// Meet transfer error
					errTime++;
				}
				// Too many errors
				if(errTime > 5){
					System.out.println("[Error] Too many transfer errors, System shut down.");
					System.exit(2);
				}
			}else {
				System.out.println("[Error] Server Bad Response...");
			}
		}
		// End connection with target server
		WaveHandsWithServer();
	}

	private void startDownload(String path, String fileSize){
		int receivePacketNum = 0;
		// Start timer
		long start = System.currentTimeMillis();
		try{
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(clientDir + path));
			bar = new ProcessBar(this.seq, Long.parseLong(fileSize), path);
			bar.initBar(false);
			while(true){
				// Receive packet file
				UDPPacket packet = receivePacket();
				if(packet == null) continue;
				// Check end or not
				byte[] data = packet.getBytes();
				if(data != null){
					String mess = new String(data);
					String[] res = mess.split(" ");
					if(res[0].equals("SUCCESS")){
						break;
					}
					bos.write(data, 0, data.length);
					bos.flush();
					// Send ACK
					UDPPacket ackPacket = new UDPPacket(this.seq++);
					ackPacket.setACK(packet.getSeq());
					ackPacket.calculateSum();
					// Increase buffer's size, check whether it's full
					receivePacketNum++;
					if(receivePacketNum == this.rwnd){
						receivePacketNum = 0;
						if(this.rwnd >= 1000){
							this.rwnd /= 2;
						}
						ackPacket.setWinSize(this.rwnd);
					}
					sendDataPacket(ackPacket);
					// Update bar
					bar.updateBar(this.seq);
				}
			}
			bos.close();
			// End timer
			long end = System.currentTimeMillis();
			double costTime = (end-start) / (1000.0f);
			// Calculate file size in MB unit
			double size = Long.parseLong(fileSize) / (1024.0f * 1024.0f);
			System.out.printf("\n[Info] Client successfully download file: %s, file Size: %.2f MB, cost time: %.2f sec\n", path, size, costTime);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	private UDPPacket receivePacket(){
		byte[] buffer = new byte[MAX_LENGTH + 1024];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
	    try {
	    	socket.receive(packet);
	    	UDPPacket udpPacket = UDPPacket.NewUDPPacket(packet.getData());
	    	if(packet != null)
	    		udpPacket.setPacket(packet);
		    return udpPacket;
	    } catch (IOException e) {
	    	return null;
	    }
	}

	private void sendPacket() throws IOException{
		// Get Busy
		this.status = true;
		socket.setSoTimeout(2500);
		// Send all the packets in queue
		while(packetQueue.size() > 0){
			int errTime = 0;  // Record onr packet send fail times
			int sendPacketNum = 0;  // Record how many packets have client sent
			int currCongestNum = 1;  // Congestion control -- slow start's init number
			UDPPacket top = packetQueue.poll();
			// Dead loop to send one packet (detect failure)
			while(true){
				// Check floe control -- client stop sign
				try{
					if(sendPacketNum == swnd){
						Thread.sleep(2500);
					}
				} catch(Exception e){
					e.printStackTrace();
				}
				byte[] byteData = top.getPacketBytes();
				DatagramPacket outPacket = new DatagramPacket(byteData, byteData.length, serverHost, serverPort);
				socket.send(outPacket);
				// Check return ack packet
				UDPPacket backPacket = receivePacket();
				if(backPacket != null && backPacket.isACK() && backPacket.checkSum()){
					sendPacketNum++;
					currCongestNum++;
					// Check flow control window size change sign
					if(backPacket.getWinSize() != 0){
						sendPacketNum = 0;
						this.swnd = backPacket.getWinSize();
					}
					// Check congestion control, whether slow start needs to incrase cwnd
					if(currCongestNum < this.ssthresh && currCongestNum % 2 == 0){
						// Keep in slow start
						this.cwnd = currCongestNum;
					}else {
						// Change into congestion avoidance
						this.cwnd++;
					}
					bar.updateBar(backPacket.getACK());
					break;
				}else {
					// Meet transfer error
					errTime++;
					// Congestion fast recovery
					this.ssthresh = this.cwnd / 2;
					this.cwnd = 1;
				}
				// Too many errors
				if(errTime > 5){
					System.out.println("[Error] Too many transfer errors, System shut down.");
					System.exit(2);
				}
			}
		}
		// Get Free
		this.status = false;
	}

	private void sendDataPacket(UDPPacket packet) throws IOException{
		byte[] data = packet.getPacketBytes();
		DatagramPacket ackPacket = new DatagramPacket(data, data.length, serverHost, serverPort);
		socket.send(ackPacket);
	}

	private boolean ShakeHandsWithServer(String serverPath) throws IOException{
		String[] address = serverPath.split(":");
		serverHost = InetAddress.getByName(address[0]);
		serverPort = Integer.parseInt(address[1]);
		int errTime = 0;
		while(true){
			socket.setSoTimeout(2500);
			// Step 1. send Pakcet_SYN_1 to server
			UDPPacket syn_packet_1 = new UDPPacket(this.seq++);
			syn_packet_1.setSYN(this.syn++);
			syn_packet_1.calculateSum();
			sendDataPacket(syn_packet_1);
			// Step 2. wait server return packet and check
			UDPPacket syn_packet_2 = receivePacket();
			if(syn_packet_2 != null && syn_packet_2.isACK() && syn_packet_2.isSYN() 
						&& syn_packet_2.getACK() == this.syn && syn_packet_2.checkSum()){
				// Step 3. send ack to server to establish it
				UDPPacket ack_packet = new UDPPacket(this.seq++);
				ack_packet.setACK(syn_packet_2.getSYN() + 1);
				ack_packet.calculateSum();
				sendDataPacket(ack_packet);
				System.out.printf("[Info] Successfully establish connections with Sever: %s, serverPort: %s\n", address[0], address[1]);
				return true;
			}else {
				System.out.println("[Error] Shake hands packet time out....");
				errTime++;
			}
			if(errTime > 5){
				System.out.println("[Error] Shake hands with server failed! Too many tansfer errors!");
				break;
			}
		}
		return false;
	}
	
	private void WaveHandsWithServer() throws IOException{
		// Init: time out
		int errTime = 0;
		while(true){
			socket.setSoTimeout(2500);
			// Step 1. client sends init fin packet to server
			UDPPacket fin_packet_1 = new UDPPacket(this.seq++);
			fin_packet_1.setFIN(this.fin++);
			fin_packet_1.calculateSum();
			sendDataPacket(fin_packet_1);
			// Step 2. wait server's response
			UDPPacket ack_packet;
			while((ack_packet = receivePacket()) == null){}
			if(ack_packet != null && ack_packet.isACK() && ack_packet.checkSum()){
				// Step 3. wait server's fin packet
				UDPPacket fin_packet_2 = receivePacket();
				if(fin_packet_2 != null && fin_packet_2.isFIN() && fin_packet_2.checkSum()){
					// Step 4. send final ack packet to server
					UDPPacket ack_packet_fin = new UDPPacket(this.seq++);
					ack_packet_fin.setACK(fin_packet_2.getSeq() + 1);
					ack_packet_fin.calculateSum();
					sendDataPacket(ack_packet_fin);
					System.out.println("[Info] Successfully quit the connection with target server....");
					break;
				}else {
					System.out.println("[Error] Wave hands packet time out....");
					errTime++;
				}
			}else {
				System.out.println("[Error] Wave hands packet time out....");
				errTime++;
			}
			if(errTime > 5){
				System.out.println("[Error] Wave hands with server failed! Too many tansfer errors!");
				return;
			}
		}
	}

	public void close(){
		socket.close();
	}

};