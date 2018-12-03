package lftp.Server;

import java.net.DatagramPacket;
import java.util.Objects;
import java.util.Arrays;
import java.io.*;

public class UDPPacket implements Serializable{
	// Define a udp packet
	private transient DatagramPacket packet;
	private String packetName;  // Name of file this packet belongs to
	private int seq;  // Sequence number
	private int ack;  // Acknowledge number
	private int syn;  // Shake hands number
	private int fin;  // Wave hands number
	private long winSize;  // Transfer window size between client and server, make flow control
	private boolean synFlag;  // Syn flag bit
	private boolean ackFlag;  // Ack flag bit
	private boolean finFlag;  // Fin flag bit
	private byte[] data;  // store packet content
	private String checksum; // check sum

	public UDPPacket(int sequence){
		this.seq = sequence;
		this.winSize = 0;
	}

	public UDPPacket(byte[] data){
		this.data = data;
	}

	// This method is used for Datagram Packet to serialize into UDPPacket
	public static UDPPacket NewUDPPacket(byte[] dPacket){
		try{
			return (UDPPacket) (new ObjectInputStream(new ByteArrayInputStream(dPacket))).readObject();
		} catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}

	public void setPacket(DatagramPacket da){
		this.packet = da;
	}

	public DatagramPacket getPacket(){
		return this.packet;
	}

	public byte[] getBytes(){
		return this.data;
	}

	public void setBytes(byte[] data_){
		this.data = data_;
	}

	public int getSeq(){
		return this.seq;
	}

	public void setSeq(int seq_){
		this.seq = seq_;
	}

	// For getting packet's serializable content
	public byte[] getPacketBytes(){
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		try (ObjectOutputStream stream = new ObjectOutputStream(bao)){
			// Serialize this UDPPacket obejct to stream bytes
			stream.writeObject(this);
			stream.flush();
			return bao.toByteArray();
		} catch(IOException e){
			e.printStackTrace();
			System.out.println("[Error] Cannot serialize this UDPPacket to string!");
		}
		return null;
	}

	public void setACK(int ack){
		this.ackFlag = true;
		this.ack = ack;
	}

	public int getACK(){
		return this.ack;
	}

	public boolean isACK(){
		return this.ackFlag;
	}

	// Calculate the UDPPacket's checksum with its header infos and data
	private String getSum(){
		String resultHex;
		int result = 0;
		// Step 1. Calculate header's sum
		result += seq + ack + syn + fin;
		// Step 2. Calculate data's sum
		if(data != null){
			for(int i=0; i<data.length; i++){
				// Make data byte signed integer
				int value = data[i] & 0xff;
				result += value;
			}
		}
		// Step 3. Make Hex string
		return Integer.toHexString(result);
	}


	public void calculateSum(){
		this.checksum = getSum();
	}

	public boolean checkSum(){
		// Calculate check sum of variables in packet
		String temp = getSum();
		// Judge whether equal to checksum
		if(temp.equals(checksum))
			return true;
		else
			return false;
	}

	public void setSYN(int in){
		this.syn = in;
		this.synFlag = true;
	}

	public int getSYN(){
		return this.syn;
	}

	public boolean isSYN(){
		return this.synFlag;
	}

	public void setFIN(int in){
		this.fin = in;
		this.finFlag = true;
	}

	public int getFIN(){
		return this.fin;
	}

	public boolean isFIN(){
		return this.finFlag;
	}

	public void setWinSize(long size){
		this.winSize = size;
	}

	public long getWinSize(){
		return this.winSize;
	}
}