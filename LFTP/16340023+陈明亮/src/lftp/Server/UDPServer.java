package lftp.Server;

import java.io.IOException;
import java.io.File;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

public class UDPServer{
	// Server's private members
	private final int MAX_LENGTH = 50 * 1024;  // Max bytes length for one packet
	private final int MAX_CONNECT_NUM = 100;  // Max connection upper bound
	private final int PORT_NUM = 8080;  // Connect port number
	private DatagramSocket socket;
	private InetAddress clientIP;  // IP of the latest client that sends packet to server
	private int clientPort;  // Port of that client
	private String serverDir = "lftp/Files/ServerFiles/";
	// Packet infos
	private int seq;  // Current handing packet's seq
	private int connectNum;
	private Queue<UDPPacket> packetQueue;  // Current sending packets buffer
	private boolean status;  // Server's state, false for free, true for busy
	private Map<String, Thread> map; // Thread storing map
	private ArrayList connectPool;  // Client connection storing array

	// Startup construct method
	public UDPServer(){
		try {
			/* Init members */
			this.socket = new DatagramSocket(PORT_NUM);
			this.status = false;
			this.seq = 0;
			this.packetQueue = new LinkedList<>();
			this.map = new HashMap<String, Thread>();
			this.connectPool = new ArrayList<String>();
			this.connectNum = 0;
		} catch(SocketException e) {
			e.printStackTrace();
		}
	}

	private UDPPacket receivePacket(){
		try {
			byte[] data = new byte[MAX_LENGTH + 1024];
			DatagramPacket packet = new DatagramPacket(data, data.length);
			socket.receive(packet);  // Wait socket to receive the packet
			UDPPacket udpPacket = UDPPacket.NewUDPPacket(packet.getData());
			udpPacket.setPacket(packet);
			return udpPacket;
		} catch(IOException e) {
			return null;
		}
	}

	private void sendBadResponse(String path) throws IOException{
		UDPPacket packet = new UDPPacket(this.seq++);
		String mess = "ERROR " + path;
		packet.setBytes(mess.getBytes());
		packet.calculateSum();
		byte[] data = packet.getPacketBytes();
		DatagramPacket outPacket = new DatagramPacket(data, data.length, this.clientIP, this.clientPort);
		socket.send(outPacket);
	}

	// Start to listen to client's request
	public void listen() throws IOException{
		// Loop to listen to client, use callback function to handle sending file to client function
		while(true){
			UDPPacket packet = receivePacket();
			if(packet != null){
				System.out.println("[Info]LFTP-Server receives packet from LFTP-Client....");
				// Read init mess
				this.clientIP = packet.getPacket().getAddress();
				this.clientPort = packet.getPacket().getPort();
				// First consider Shake hand and Wave hand packets
				if(packet.isSYN()){
					// Invoke shake hands function to process
					shakeHandswithClient(packet.getSYN());
					continue;
				}else if(packet.isFIN()){
					// Invoke wave hands function to process
					waveHandswithClient(packet.getSeq());
					continue;
				}
				// Then process packet with data content or ACK Packets
				// Parse packet's type: beginFlag, fileContent or successFlag
				Thread tt = null;
				UploadThread st = null;  DownloadThread dt = null;
				String address = clientIP + ":" + clientPort;
				if(packet.getBytes() == null && packet.isACK() && packet.checkSum()){
					// Check shake hands/wave hands ack, or simple download ack packets
					if(connectPool.contains(address)){
						tt = map.get(clientIP.toString() + clientPort);
						dt = (DownloadThread) tt;
						dt.receivePacket(packet);
						dt.run();
						this.seq++;
						continue;
					}
				}
				String packetMess = new String(packet.getBytes());
				String[] res = packetMess.split(" ");
				File serverFile = null;
				byte[] buff;
				// Connection validation check
				if(!connectPool.contains(address)){
					System.out.printf("[Warning] The client %s connection no found, cannot start the data transferation!\n", address);
					continue;
				}
				// Open mupltiThread to deal with orders
				switch(res[0]){
					case "BEGIN":
						// Open upload thread for server to receive files from server
						tt = new UploadThread(packet, res[1], this.seq, packet.getSeq(), packet.getWinSize());
						map.put(clientIP.toString() + clientPort, tt);
						tt.start();
						st = (UploadThread) tt;
						st.startThread();
						break;
					case "SUCCESS":
						// Client upload success, end upload thread
						tt = map.get(clientIP.toString() + clientPort);
						st = (UploadThread) tt;
						st.endThread(this.seq, packet.getSeq());
						break;
					case "GET":
						// Check client download request, check file existion
						serverFile = new File(serverDir + res[1]);
						if(!serverFile.exists()){
							// Client bad request
							sendBadResponse(res[1]);
						}else {
							// Open download thread for server to send files to client
							tt = new DownloadThread(packet, res[1], this.seq, packet.getSeq());
							map.put(clientIP.toString() + clientPort, tt);
							dt = (DownloadThread) tt;
							dt.startThread();
							dt.start();
						}
						break;
					default:
						// Client's uploading packets, handle and send to the speical thread
						tt = map.get(clientIP.toString() + clientPort);
						st = (UploadThread) tt;
						st.receivePacket(packet.getBytes(), this.seq, packet.getSeq());
						st.run();
				}
			}
		}
	}

	private void shakeHandswithClient(int syn) throws IOException{
		// Init: set time out
		int errTime = 0;
		while(true){
			socket.setSoTimeout(2500);
			// Step 1. send ack and syn packet
			UDPPacket ack_syn_packet = new UDPPacket(this.seq++);
			ack_syn_packet.setACK(syn + 1);
			ack_syn_packet.setSYN(syn);
			ack_syn_packet.calculateSum();
			sendDataPacket(ack_syn_packet);
			// Step 2. wait ack packet return
			UDPPacket ack_packet = receivePacket();
			if(ack_packet != null && ack_packet.isACK() && ack_packet.getACK() == syn + 1 
						&& ack_packet.checkSum()){
				if(connectNum == MAX_CONNECT_NUM){
					System.out.println("[Error] Server connection overflow, cannot establish new connection!");
				}else {
					connectNum++;
					connectPool.add(clientIP + ":" + clientPort);
					System.out.printf("[Info] Server successfully establish connection with Client, IP:%s, port:%s\n", clientIP.toString(), clientPort);
				}
				break;
			}else {
				errTime++;
			}
			if(errTime > 5){
				System.out.println("[Error] Server shake hands with client failed! System shut down!");
				System.exit(-1);
			}
		}
	}

	private void waveHandswithClient(int seq_) throws IOException{
		// Init: set time out
		int errTime = 0;
		while(true){
			// Step 1. send ack and fin packets to server
			UDPPacket ack_packet = new UDPPacket(this.seq++);
			ack_packet.setACK(seq_+1);
			ack_packet.calculateSum();
			sendDataPacket(ack_packet);
			String addr = clientIP + ":" + clientPort;
			
			UDPPacket fin_packet = new UDPPacket(this.seq++);
			fin_packet.setFIN(seq);
			fin_packet.calculateSum();
			sendDataPacket(fin_packet);
			// Step 2. wait for response
			UDPPacket packet = receivePacket();
			if(packet != null && packet.isACK() && packet.checkSum()){
				for(int i=0; i<connectPool.size(); i++){
					if(connectPool.get(i).equals(addr)){
						connectPool.remove(i);
						break;
					}
				}
				System.out.printf("[Info] Server successfully delete connection with Client: %s\n", addr);
				break;
			}else {
				errTime++;
			}
			if(errTime > 5){
				System.out.println("[Error] Server wave hands with client failed! System shut down!");
				System.exit(-1);
			}
		}
	}

	private void sendDataPacket(UDPPacket packet) throws IOException{
		byte[] data = packet.getPacketBytes();
		DatagramPacket ackPacket = new DatagramPacket(data, data.length, clientIP, clientPort);
		socket.send(ackPacket);
	}

	public void close(){
		socket.close();
	}
	
};