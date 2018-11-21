package lftp.Server;

import java.io.*;
import java.net.*;
import lftp.Server.UDPPacket;

public class DownloadThread extends Thread{
	private RandomAccessFile accessFile;
	private DatagramSocket socket;
	private UDPPacket ackPacket;
	private InetAddress clientIP;
	private int clientPort;
	private int seq;
	private int initACK;
	private int readPos;
	private String fileName;
	private int MAX_LENGTH = 50 * 1024;
	private final String serverDir = "lftp/Files/ServerFiles/";

	public DownloadThread(UDPPacket packet, String name, int seq, int ack){
		this.clientIP = packet.getPacket().getAddress();
		this.clientPort = packet.getPacket().getPort();
		this.fileName = name;
		this.initACK = ack;
		this.seq = seq;
		try{
			this.accessFile = new RandomAccessFile(serverDir + fileName, "r");
			this.socket = new DatagramSocket();
			this.ackPacket = null;
		} catch(Exception e){
			e.printStackTrace();
		}
	}

	@Override
	public void run(){
		try{
			byte[] buffer = new byte[MAX_LENGTH];
			socket.setSoTimeout(2500);
			if(ackPacket != null && ackPacket.isACK()){
				int errTime = 0;
				UDPPacket packet = new UDPPacket(this.seq++);
				readPos = accessFile.read(buffer, 0, buffer.length);
				if(readPos != -1){
					packet.setBytes(buffer);
				}else {
					String end = "SUCCESS";
					packet.setBytes(end.getBytes());
				}
				byte[] data = packet.getPacketBytes();
				DatagramPacket outPacket = new DatagramPacket(data, data.length, this.clientIP, this.clientPort);;
				socket.send(outPacket);
				// Reset ack packet
				ackPacket = null;
			}
		} catch(Exception e){
			e.printStackTrace();
		}
		
	}

	// send init ack packet, init thread
	public void startThread(){
		try{
			UDPPacket pa = new UDPPacket(this.seq++);
			pa.setACK(initACK);
			File file = new File(serverDir + fileName);
			String str = "OK " + file.length();
			pa.setBytes(str.getBytes());
			this.ackPacket = pa;
			byte[] data = pa.getPacketBytes();
			DatagramPacket outPacket = new DatagramPacket(data, data.length, this.clientIP, this.clientPort);
			socket.send(outPacket);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	// Help receive ack UDPPacket
	public void receivePacket(UDPPacket ackPacket){
		this.ackPacket = ackPacket;
	}

}