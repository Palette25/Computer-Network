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
	private String fileName;
	private String serverDir = "lftp/Files/ServerFiles/";
	private BufferedOutputStream bos;
	private boolean end;

	public UploadThread(UDPPacket packet, String fileName, int seq, int ack){
		this.end = false;
		this.fileName = fileName;
		this.content = null;
		this.seq = seq;
		this.ack = ack;
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
					sendACKPacket(this.seq, this.ack);
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
		sendACKPacket(this.seq, this.ack);
	}

	public void endThread(int seq, int ack){
		this.end = true;
		try{
			bos.close();
		} catch(IOException e){
			e.printStackTrace();
		}
		System.out.printf("[Info]LFTP-Server successfully received large file: %s\n", fileName);
		sendACKPacket(seq, ack);
	}

	public void sendACKPacket(int seq, int ack_){
		try{
			UDPPacket ackPacket = new UDPPacket(seq);
			ackPacket.setACK(ack_);
			byte[] data = ackPacket.getPacketBytes();
			DatagramPacket packet = new DatagramPacket(data, data.length, this.clientIP, this.clientPort);
			socket.send(packet);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

}