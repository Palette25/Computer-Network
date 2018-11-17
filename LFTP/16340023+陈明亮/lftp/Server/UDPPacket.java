package lftp.Server;

import java.net.DatagramPacket;
import java.util.Objects;
import java.io.*;

public class UDPPacket implements Serializable{
	// Define a udp packet
	private transient DatagramPacket packet;
	private String packetName;  // Name of file this packet belongs to
	private int seq;  // Sequence number
	private int ack;  // Acknowledge number
	private int winSize;  // Windows size
	private boolean synFlag;  // Syn flag bit
	private boolean ackFlag;  // Ack flag bit
	private boolean finFlag;  // Fin flag bit
	private byte[] data;
	private int[] checksum;

	public UDPPacket(int sequence){
		this.seq = sequence;
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

	public void setSYN(boolean in){
		this.synFlag = in;
	}

	public boolean getSYN(){
		return this.synFlag;
	}

	public void setFIN(boolean in){
		this.finFlag = in;
	}

	public boolean getFIN(){
		return this.finFlag;
	}

	

}