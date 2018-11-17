import java.io.*;
import lftp.Client.UDPClient;
import lftp.Client.ProcessBar;

import java.util.Scanner;

public class LFTP_Client{
	private static UDPClient client;

	public static void main(String[] args){
		// Init the client
		client = new UDPClient();
		// Scan user's input
		Scanner input = new Scanner(System.in);
		String tip = "-------------- Welcome to LFTP --------------\nEnter commands to start using LFTP Client, \"help\" for details\nLFTP-Client$: ";
		System.out.printf(tip);
		String command = input.nextLine();
		while(!command.equals("exit")){
			if(!Parse(command)) break;
			System.out.printf("LFTP-Client$: ");
			command = input.nextLine();
		}
		client.close();
		System.out.println("Exit.");
	}

	public static void Usage(){
		String help0 = "Available commands:\n\tLFTP lsend 127.0.0.1:8080 \"fileName\" -- send file to server\n\t",
				help1 = "LFTP lget 127.0.0.1:8080 \"fileName\" -- get file from server\n\texit -- leave LFTP-Client\n\thelp -- get help details";
		System.out.println(help0 + help1);
	}

	public static boolean Parse(String input){
		String[] sp = input.split(" ");
		String res = "[Error] Unknown command: " + input + "\n";
		if(sp.length == 1){
			if(sp[0].equals("help")){
				Usage();
				res = "";
			} else if(sp[0].equals("exit")){
				return false;
			}
		} else if(sp.length == 4){
			try {
				switch(sp[1]){
					case "lsend": 
						System.out.println("[Info] Client is ready to send file to server....");
						client.handleSendRequest(sp[3]); 
						res = ""; 
						break;
					case "lget": 
						System.out.println("[Info] Client is ready to get file from server....");
						client.handleGetRequest(sp[3]); 
						res = ""; 
				}
			} catch (Exception e){
				e.printStackTrace();
				return false;
			}
		} else {
			System.out.println("[Error] Command parameters format error!\n");
			return true;
		}
		System.out.printf(res);
		return true;
	}
};