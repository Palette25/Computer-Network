import java.io.*;
import lftp.Server.UDPServer;
import lftp.Server.UploadThread;
import lftp.Server.UDPPacket;
import lftp.Server.DownloadThread;

import java.util.Scanner;

public class LFTP_Server{
	private static UDPServer server;

	public static void main(String[] args){
		// Start the server
		server = new UDPServer();
		// Start server port listen
		System.out.println("[Info] LFTP Server start up at 127.0.0.1:8080...");
		try{
			server.listen();
		} catch(IOException e){
			e.printStackTrace();
		}
	}
};