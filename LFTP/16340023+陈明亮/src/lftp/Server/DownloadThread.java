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
	private long swnd;  // Server's send windos size, make flow control
	private int sendPacketNum = 0;  // The packet haved sent number
	private int cwnd = 1;  // Server side's congestion window size
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
			if(ackPacket != null && ackPacket.isACK() && ackPacket.checkSum()){
				int errTime = 0;
				// change swnd notified by client
				if(ackPacket.getWinSize() != 0)
					this.swnd = ackPacket.getWinSize();
				UDPPacket packet = new UDPPacket(this.seq++);
				readPos = accessFile.read(buffer, 0, buffer.length);
				if(readPos != -1){
					packet.setBytes(buffer);
				}else {
					String end = "SUCCESS";
					packet.setBytes(end.getBytes());
					System.out.printf("[Info]LFTP-Server successfully sent large file: %s\n", fileName);
					accessFile.close();
				}
				packet.calculateSum();
				byte[] data = packet.getPacketBytes();
				DatagramPacket outPacket = new DatagramPacket(data, data.length, this.clientIP, this.clientPort);
				socket.send(outPacket);
				sendPacketNum++;
				// Make flow control, check whether it's time to sleep server
				if(sendPacketNum == this.swnd){
					sendPacketNum = 0;
					Thread.sleep(2500);
				}
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
			this.swnd = file.length() / MAX_LENGTH;
			String str = "OK " + file.length();
			pa.setBytes(str.getBytes());
			this.ackPacket = pa;
			pa.calculateSum();
			pa.setWinSize(swnd);
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