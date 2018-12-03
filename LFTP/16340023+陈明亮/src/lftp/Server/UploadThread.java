package lftp.Server;

import java.io.*;
import java.net.*;
import lftp.Server.UDPPacket;

public class UploadThread extends Thread{
	private DatagramSocket socket;
	private byte[] content;
	private InetAddress clientIP;
	private int clientPort;
	private int ack;
	private int seq;
	private long rwnd;  // UploadThread's receive windows size, make flow control
	private int receivePacketNum;  //Record how many packets have server received
	private String fileName;
	private String serverDir = "lftp/Files/ServerFiles/";
	private BufferedOutputStream bos;
	private boolean end;

	public UploadThread(UDPPacket packet, String fileName, int seq, int ack, long rwnd){
		this.end = false;
		this.fileName = fileName;
		this.content = null;
		this.seq = seq;
		this.ack = ack;
		this.rwnd = rwnd;
		this.receivePacketNum = 0;
		try{
			this.socket = new DatagramSocket();
			this.bos = new BufferedOutputStream(new FileOutputStream(serverDir + fileName));
			this.clientIP = packet.getPacket().getAddress();
			this.clientPort = packet.getPacket().getPort();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	@Override
	public void run(){
		try{
			if(!this.end){
				if(this.content != null){
					// Write content
					bos.write(content, 0, content.length);
					bos.flush();
					content = null;
					receivePacketNum++;
					/* Start flow control, notify client not to send anymore,
					* 	Cause it reaches the window size, and half divide the window size
					* 	to make speed lower, 
					*/
					if(receivePacketNum == rwnd){
						receivePacketNum = 0;
						if(rwnd >= 1000){
							rwnd = rwnd / 2;
						}
						sendACKPacket(this.seq, this.ack, rwnd);
					}else {
						// Else stay normal receiving state
						sendACKPacket(this.seq, this.ack, 0);
					}
				}
			}
		} catch(IOException e){
			e.printStackTrace();
		} 
	}

	public void receivePacket(byte[] content, int seq, int ack){
		this.seq = seq;
		this.ack = ack;
		this.content = content;
	}

	public void startThread(){
		sendACKPacket(this.seq, this.ack, 0);
	}

	public void endThread(int seq, int ack){
		this.end = true;
		try{
			bos.close();
		} catch(IOException e){
			e.printStackTrace();
		}
		System.out.printf("[Info]LFTP-Server successfully received large file: %s\n", fileName);
		sendACKPacket(seq, ack, 0);
	}

	public void sendACKPacket(int seq, int ack_, long winSize){
		try{
			UDPPacket ackPacket = new UDPPacket(seq);
			ackPacket.setACK(ack_);
			ackPacket.calculateSum();
			// Notify client change send window size or not
			if(winSize != 0)
				ackPacket.setWinSize(winSize);
			byte[] data = ackPacket.getPacketBytes();
			DatagramPacket packet = new DatagramPacket(data, data.length, this.clientIP, this.clientPort);
			socket.send(packet);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	

}