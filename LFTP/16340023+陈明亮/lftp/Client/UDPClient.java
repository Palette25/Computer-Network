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
	private InetAddress host;
	private final int PORT_NUM = 8081;
	private final int MAX_LENGTH = 50 * 1024;  // Max bytes length for one packet
	private DatagramSocket socket;
	private String clientDir = "lftp/Files/ClientFiles/";

	private int seq;
	private Queue<UDPPacket> packetQueue;  // Current sending packets buffer
	private boolean status;  // Client's state, false for free, true for busy
	private ProcessBar bar;  // The process bar of file transfering

	// Startup constructed method
	public UDPClient(){
		try {
			socket = new DatagramSocket(PORT_NUM);
			host = InetAddress.getByName("127.0.0.1");
			packetQueue = new LinkedList<>();
		} catch(SocketException e) {
			e.printStackTrace();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	public void handleSendRequest(String path) throws IOException{
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
		// Begin sendind file, send begin flag
		UDPPacket packet0 = new UDPPacket(this.seq++);
		String begin = "Begin " + path;
		packet0.setBytes(begin.getBytes());
		this.packetQueue.add(packet0);
		bar = new ProcessBar(this.seq, file.length(), path);
		bar.initBar();
		// Send file by dividing into pieces
		RandomAccessFile accessFile = new RandomAccessFile(clientDir + path, "r");
		byte[] buffer = new byte[MAX_LENGTH];
		int readPos = -1;
		while((readPos = accessFile.read(buffer, 0, buffer.length)) != -1){
			UDPPacket packet = new UDPPacket(this.seq);
			this.seq++;
			packet.setBytes(buffer);
			this.packetQueue.add(packet);
			// Send file block one by one
			if(this.status == false)
				sendPacket();
		}
		// End file sending, send success flag
		UDPPacket packet1 = new UDPPacket(this.seq++);
		String end = "Success " + path;
		packet1.setBytes(end.getBytes());
		this.packetQueue.add(packet1);
		sendPacket();
		System.out.printf("\n[Info]LFTP-Client successfully sends large file: %s\n", path);
	}

	public void handleGetRequest(String path) throws IOException{
		// Send server a download request
		UDPPacket getRequestPacket = new UDPPacket(this.seq);
		this.seq++;
		String str = "GET: " + path;
		getRequestPacket.setBytes(str.getBytes());
		this.packetQueue.add(getRequestPacket);
		// Check busy or not
		if(this.status == false)
			sendPacket();
	}

	private UDPPacket receivePacket(){
		byte[] buffer = new byte[MAX_LENGTH];
		DatagramPacket packet = new DatagramPacket(buffer, MAX_LENGTH);
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
			UDPPacket top = packetQueue.poll();
			// Dead loop to send one packet (detect failure)
			while(true){
				byte[] byteData = top.getPacketBytes();
				DatagramPacket outPacket = new DatagramPacket(byteData, byteData.length, InetAddress.getLocalHost(), 8080);
				socket.send(outPacket);
				// Check return ack packet
				UDPPacket backPacket = receivePacket();
				if(backPacket != null && backPacket.isACK()){
					//System.out.printf("[Info]LFTP-Client receives ACK packet from LFTP-Server, packet sequence:%d\n", backPacket.getACK());
					bar.updateBar(backPacket.getACK());
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
			}
		}
		// Get Free
		this.status = false;
	}

	private void sendACKPacket(UDPPacket packet) throws IOException{
		byte[] data = packet.getPacketBytes();
		DatagramPacket ackPacket = new DatagramPacket(data, data.length, host, PORT_NUM);
		socket.send(ackPacket);
	}

	private void newFile(String fileName){

	}

	public void close(){
		socket.close();
	}

};