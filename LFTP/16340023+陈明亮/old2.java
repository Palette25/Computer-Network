package com.bill.udp.client;
 
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
 
import com.bill.udp.util.UDPUtils;
/**
 * Test File transfer of Client
 * @author Bill QQ:593890231
 * @since v1.0 2014/09/21
 */
public class UDPClient {
	
	private static final String SEND_FILE_PATH = "D:/2013.mkv";
	
	public static void main(String[] args){
		long startTime = System.currentTimeMillis();
		
		byte[] buf = new byte[UDPUtils.BUFFER_SIZE];
		byte[] receiveBuf = new byte[1];
		
		RandomAccessFile accessFile = null;
		DatagramPacket dpk = null;
		DatagramSocket dsk = null;
		int readSize = -1;
		try {
			accessFile = new RandomAccessFile(SEND_FILE_PATH,"r");
			dpk = new DatagramPacket(buf, buf.length,new InetSocketAddress(InetAddress.getByName("localhost"), UDPUtils.PORT + 1));
			dsk = new DatagramSocket(UDPUtils.PORT, InetAddress.getByName("localhost"));
			int sendCount = 0;
			while((readSize = accessFile.read(buf,0,buf.length)) != -1){
				dpk.setData(buf, 0, readSize);
				dsk.send(dpk);
				// wait server response 
				{
					while(true){
						dpk.setData(receiveBuf, 0, receiveBuf.length);
						dsk.receive(dpk);
						
						// confirm server receive
						if(!UDPUtils.isEqualsByteArray(UDPUtils.successData,receiveBuf,dpk.getLength())){
							System.out.println("resend ...");
							dpk.setData(buf, 0, readSize);
							dsk.send(dpk);
						}else
							break;
					}
				}
				
				System.out.println("send count of "+(++sendCount)+"!");
			}
			// send exit wait server response
			while(true){
				System.out.println("client send exit message ....");
				dpk.setData(UDPUtils.exitData,0,UDPUtils.exitData.length);
				dsk.send(dpk);
 
				dpk.setData(receiveBuf,0,receiveBuf.length);
				dsk.receive(dpk);
				// byte[] receiveData = dpk.getData();
				if(!UDPUtils.isEqualsByteArray(UDPUtils.exitData, receiveBuf, dpk.getLength())){
					System.out.println("client Resend exit message ....");
					dsk.send(dpk);
				}else
					break;
			}
		}catch (Exception e) {
			e.printStackTrace();
		} finally{
			try {
				if(accessFile != null)
					accessFile.close();
				if(dsk != null)
					dsk.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		long endTime = System.currentTimeMillis();
		System.out.println("time:"+(endTime - startTime));
	}
}


package com.bill.udp.server;
 
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
 
import com.bill.udp.util.UDPUtils;
 
/**
 * Test file transfer of Server 
 * @author Bill  QQ:593890231
 * @since v1.0 2014/09/21
 * 
 */
public class UDPServer {
	
	private static final String SAVE_FILE_PATH = "E:/2013.mkv";
	
	public static void main(String[] args) {
		
		byte[] buf = new byte[UDPUtils.BUFFER_SIZE];
		
		DatagramPacket dpk = null;
		DatagramSocket dsk = null;
		BufferedOutputStream bos = null;
		try {
			
			dpk = new DatagramPacket(buf, buf.length,new InetSocketAddress(InetAddress.getByName("localhost"), UDPUtils.PORT));
			dsk = new DatagramSocket(UDPUtils.PORT + 1, InetAddress.getByName("localhost"));
			bos = new BufferedOutputStream(new FileOutputStream(SAVE_FILE_PATH));
			System.out.println("wait client ....");
			dsk.receive(dpk);
			
			int readSize = 0;
			int readCount = 0;
			int flushSize = 0;
			while((readSize = dpk.getLength()) != 0){
				// validate client send exit flag  
				if(UDPUtils.isEqualsByteArray(UDPUtils.exitData, buf, readSize)){
					System.out.println("server exit ...");
					// send exit flag 
					dpk.setData(UDPUtils.exitData, 0, UDPUtils.exitData.length);
					dsk.send(dpk);
					break;
				}
				
				bos.write(buf, 0, readSize);
				if(++flushSize % 1000 == 0){ 
					flushSize = 0;
					bos.flush();
				}
				dpk.setData(UDPUtils.successData, 0, UDPUtils.successData.length);
				dsk.send(dpk);
				
				dpk.setData(buf,0, buf.length);
				System.out.println("receive count of "+ ( ++readCount ) +" !");
				dsk.receive(dpk);
			}
			
			// last flush 
			bos.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally{
			try {
				if(bos != null)
					bos.close();
				if(dsk != null)
					dsk.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		
	}
}
