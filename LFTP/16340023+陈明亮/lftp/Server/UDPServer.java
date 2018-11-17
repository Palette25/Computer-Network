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
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class UDPServer{
	// Server's private members
	private final int MAX_LENGTH = 50 * 1024;  // Max bytes length for one packet
	private final int PORT_NUM = 8080;  // Connect port number
	private DatagramSocket socket;
	private InetAddress clientIP;  // IP of the latest client that sends packet to server
	private int clientPort;  // Port of that client
	private String serverDir = "lftp/Files/ServerFiles/";
	// Packet infos
	private int seq;  // Current handing packet's seq
	private Queue<UDPPacket> packetQueue;  // Current sending packets buffer
	private int cwnd;  // Windows size;
	private boolean status;  // Server's state, false for free, true for busy

	public interface listenCallBack{
		boolean handleDownload(UDPPacket pa);
	}

	// Startup construct method
	public UDPServer(){
		try {
			/* Init members */
			this.socket = new DatagramSocket(PORT_NUM);
			this.status = false;
			this.seq = 0;
			this.packetQueue = new LinkedList<>();
		} catch(SocketException e) {
			e.printStackTrace();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private void sendFileToClient(String packetContent) throws IOException{
		String path = packetContent.substring(3, packetContent.length());
		// Read this file in server directory
		File file = new File(serverDir + path);
		
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

	private void sendPacket(UDPPacket packet) throws IOException{
		byte[] data = packet.getPacketBytes();
		DatagramPacket outPacket = new DatagramPacket(data, data.length, this.clientIP, this.clientPort);
		socket.send(outPacket);
	}

	// Start to listen to client's request
	public void listen(listenCallBack callback) throws IOException{
		// Loop to listen to client, use callback function to handle sending file to client function
		BufferedOutputStream bos = null;
		while(true){
			UDPPacket packet = receivePacket();
			if(packet != null){
				System.out.println("[Info]LFTP-Server receives packet from LFTP-Client....");
				//System.out.printf("[Info]LFTP-Server receives packet contents: %s\n", new String(packet.getBytes()));
				// Parse packet's type: beginFlag, fileContent or successFlag
				String packetMess = new String(packet.getBytes());
				String[] res = packetMess.split(" ");
				byte[] buff;
				switch(res[0]){
					case "Begin":
						bos = new BufferedOutputStream(new FileOutputStream(serverDir + res[1]));
						break;
					case "Success":
						bos.close();
						System.out.printf("[Info]LFTP-Server successfully received large file: %s\n", res[1]);
						break;
					default:
						buff = packet.getBytes();
						bos.write(buff, 0, buff.length);
						bos.flush();
				}
				// Set server's target s Sending ip and port
				this.clientIP = packet.getPacket().getAddress();
				this.clientPort = packet.getPacket().getPort();
				UDPPacket ackPacket = new UDPPacket(this.seq);
				this.seq++;  // Increasing the seq number;
				ackPacket.setACK(packet.getSeq());
				try {
					sendPacket(ackPacket);
					/*if(callback.handleDownload(packet)){
						sendFileToClient(packet.getData());
					}*/
				} catch(IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void close(){
		socket.close();
	}
	
};