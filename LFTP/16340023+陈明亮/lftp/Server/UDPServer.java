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
	private Map<String, Thread> map;

	// Startup construct method
	public UDPServer(){
		try {
			/* Init members */
			this.socket = new DatagramSocket(PORT_NUM);
			this.status = false;
			this.seq = 0;
			this.packetQueue = new LinkedList<>();
			this.map = new HashMap<String, Thread>();
		} catch(SocketException e) {
			e.printStackTrace();
		} catch(IOException e) {
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
		byte[] data = packet.getPacketBytes();
		DatagramPacket outPacket = new DatagramPacket(data, data.length, this.clientIP, this.clientPort);
		socket.send(outPacket);
	}

	// Start to listen to client's request
	public void listen() throws IOException{
		// Loop to listen to client, use callback function to handle sending file to client function
		BufferedOutputStream bos = null;
		while(true){
			UDPPacket packet = receivePacket();
			if(packet != null){
				System.out.println("[Info]LFTP-Server receives packet from LFTP-Client....");
				// Parse packet's type: beginFlag, fileContent or successFlag
				Thread tt = null;
				UploadThread st = null;  DownloadThread dt = null;
				if(packet.getBytes() == null){
					tt = map.get(clientIP.toString() + clientPort);
					dt = (DownloadThread) tt;
					dt.receivePacket(packet);
					dt.run();
					this.seq++;
					continue;
				}
				String packetMess = new String(packet.getBytes());
				String[] res = packetMess.split(" ");
				File serverFile = null;
				byte[] buff;
				this.clientIP = packet.getPacket().getAddress();
				this.clientPort = packet.getPacket().getPort();
				switch(res[0]){
					case "BEGIN":
						// Open upload thread for server to receive files from server
						tt = new UploadThread(packet, res[1], this.seq, packet.getSeq());
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

	public void close(){
		socket.close();
	}
	
};