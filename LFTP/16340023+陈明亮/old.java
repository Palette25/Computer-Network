package cn.zhenly.LFTP.NetUDP;

import java.io.Serializable;

// FileChunk 文件块数据
public class FileChunk implements Serializable {
  public String name;
  public int id;
  public byte[] data;
}

package cn.zhenly.LFTP.NetUDP;

import java.io.Serializable;

// FileMeta 文件元信息
public class FileMeta implements Serializable {
  public String name;
  public int size;
  public int chunkCount;
}

/* CMD -- Send */
package cn.zhenly.LFTP.Cmd;

import cn.zhenly.LFTP.NetUDP.NetUDP;
import cn.zhenly.LFTP.NetUDP.UDPPacket;
import picocli.CommandLine.*;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Command(name = "lsend", mixinStandardHelpOptions = true, description = "Send file to server.")
public class Send implements Runnable {

  @Option(names = {"-s", "--server"}, description = "Server location.")
  private
  String server;

  @Parameters(description = "file path")
  private
  List<String> files;

  private InetAddress targetIP;

  private int targetPort = 3000; // 默认端口

  @Override
  public void run() {
    System.out.println(server);
    String[] targetAddress = server.split(":");
    // 解析端口
    if (targetAddress.length == 2) {
      try {
        targetPort = Integer.parseInt(targetAddress[1]);
      } catch (NumberFormatException e) {
        System.out.printf("[ERROR] Invalid server port: %s%n", targetAddress[1]);
      }
    } else if (targetAddress.length > 2 || targetAddress.length < 1) {
      System.out.printf("[ERROR] Invalid server location: %s%n", server);
    }
    // 解析地址
    try {
      targetIP = InetAddress.getByName(targetAddress[0]);
    } catch (UnknownHostException e) {
      System.out.printf("[ERROR] Invalid server location: %s%n", server);
      return;
    }
      System.out.printf("[INFO] Server location: %s:%d%n", targetIP.toString(), targetPort);
    // 解析文件
    if (files.size() == 0) {
      System.out.println("[ERROR] no file to send.");
      return;
    }
    // 发送文件
    for (String f : files) {
      System.out.printf("[INFO] File: %s ready to send.%n", f);
      File file = new File(f);
      if (!file.exists() || !file.isFile()) {
        System.out.printf("[ERROR] %s is not a file.%n", f);
      }
      InputStream iStream;
      try {
        iStream = new FileInputStream(file);
      } catch (FileNotFoundException e) {
        System.out.printf("[ERROR] Can't read file: %s%n", f);
      }
      wantToSendFile(file.getName());
    }
    try {
      NetUDP netUDP;
      netUDP = new NetUDP(9000, targetIP, targetPort);
      netUDP.send("Hello, LFTP".getBytes(), null);
      // netUDP.send("Hello, LFTP2".getBytes());
      // netUDP.send("Hello, LFTP3".getBytes());
      netUDP.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  private void wantToSendFile(String name) {
    try {
      NetUDP netUDP;
      netUDP = new NetUDP(9000, targetIP, targetPort);
      netUDP.send("Hello, LFTP".getBytes(), (UDPPacket data) -> {
        System.out.println(data.getSeq());
        System.out.println(data.getAck());
        System.out.println(data.isACK());
      });
      // netUDP.send("Hello, LFTP2".getBytes());
      // netUDP.send("Hello, LFTP3".getBytes());
      netUDP.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}

/* CMD -- Server*/
package cn.zhenly.LFTP.Cmd;

import cn.zhenly.LFTP.NetUDP.NetUDP;
import picocli.CommandLine.*;

import java.io.File;

@Command(name = "server", mixinStandardHelpOptions = true, description = "Send and receive big file by udp.")
public class Server implements Runnable {

  @Option(names = {"-p", "--port"}, description = "Server listen port.")
  int port;


  @Option(names = {"-d", "--data"}, description = "Server data dir.")
  String data;

  @Override
  public void run() {
    if (port <= 1024 || data == null) {
      System.out.println("[ERROR] invalid port or data");
      return;
    }
    File file = new File(data);
    if (!file.exists()) {
      if (!file.mkdir()) {
        System.out.println("[ERROR] Can't make directory " + data + ".");
        return;
      }
    } else if (!file.isDirectory()) {
      System.out.println("[ERROR] File " + data + " has exist, can't create directory here.");
      return;
    }
    System.out.println("[INFO] Data directory: " + data);
    NetUDP netUDP = new NetUDP(port);
    System.out.println("[INFO] Listening in localhost:" + port);
    netUDP.listen();
  }
}

/* NetUDP */
package cn.zhenly.LFTP.NetUDP;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.Queue;

public class NetUDP {
  private int seq; // 包序号
  private int targetPort; // 目标端口
  private InetAddress targetIP; // 目标地址
  private DatagramSocket socket;
  private Queue<UDPPacket> bufferPackets; // 发送缓冲区
  private boolean running; // 是否在发送
  private int cwnd; // 窗口大小

  public NetUDP(int port) {
    initSocket(port);
  }

  public NetUDP(int port, InetAddress targetIP, int targetPort) {
    initSocket(port);
    setTarget(targetIP, targetPort);
  }

  // 设置目标
  private void setTarget(InetAddress targetIP, int targetPort) {
    this.targetPort = targetPort;
    this.targetIP = targetIP;
  }

  public void setTimeOut(int timeout) {
    try {
      this.socket.setSoTimeout(timeout);
    } catch (SocketException e) {
      e.printStackTrace();
    }
  }

  // 初始化自身socket
  private void initSocket(int port) {
    this.running = false;
    this.seq = 0;
    this.bufferPackets = new LinkedList<>();
    try {
      socket = new DatagramSocket(port);
    } catch (SocketException e) {
      e.printStackTrace();
    }
  }

  private UDPPacket UDPReceive() {
    byte[] buf = new byte[1024];
    DatagramPacket p = new DatagramPacket(buf, 1024);
    try {
      socket.receive(p);
      UDPPacket packet = UDPPacket.ReadByte(p.getData());
      if (packet != null) {
        packet.setPacket(p);
      }
      return packet;
    } catch (IOException e) {
      return null;
    }
  }

  public void listen() {
    while (true) {
      UDPPacket data = UDPReceive();
      System.out.println("server received data from client：");
      if (data != null) {
        System.out.println(data.getPacket().getAddress().getHostAddress() + ":" + data.getPacket().getPort());
        System.out.println(new String(data.getData()));
        System.out.println(data.getSeq());
        setTarget(data.getPacket().getAddress(), data.getPacket().getPort());
        UDPPacket ackPacket = new UDPPacket(seq++);
        ackPacket.setAck(data.getSeq());
        try {
          UDPSend(ackPacket);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public void send(byte[] content, UDPPacket.ACKCallBack callBack) throws IOException {
    UDPPacket packet = new UDPPacket(seq++);
    packet.setCallBack(callBack);
    packet.setData(content);
    this.bufferPackets.add(packet);
    if (!running) {
      run();
    }
  }

  // 发送数据
  private void run() throws IOException {
    this.running = true;
    socket.setSoTimeout(2000);
    while (bufferPackets.size() > 0) {
      UDPPacket packet = bufferPackets.poll();
      int errorCount = 0;
      while (true) {
        UDPSend(packet);
        UDPPacket rec = UDPReceive();
        if (rec != null && rec.isValid() && rec.isACK() && rec.getAck() == packet.getSeq()) {
          if (packet.getCallBack() != null) {
            packet.getCallBack().success(rec);
          }
          break;
        } else {
          errorCount++;
        }
        if (errorCount > 5) {
          System.out.println("[ERROR] System error.");
          System.exit(-1);
        }
      }
    }
    this.running = false;
  }

  private void UDPSend(@NotNull UDPPacket packetData) throws IOException {
    byte[] data = packetData.getByte();
    DatagramPacket packet = new DatagramPacket(data, data.length, this.targetIP, this.targetPort);
    socket.send(packet);
  }

  public void close() {
    socket.close();
  }
}

/* UDPPacket */
package cn.zhenly.LFTP.NetUDP;

import java.io.*;
import java.net.DatagramPacket;

// 基本数据包
public class UDPPacket implements Serializable {
  private static final transient String TEMP_ENCODING = "ISO-8859-1";
  private static final transient String DEFAULT_ENCODING = "UTF-8";
  private transient ACKCallBack callBack;
  private transient DatagramPacket packet;
  private int winSize; // 窗口大小 (拥塞控制)
  private int seq; // 序列号
  private int ack; // 确认号
  private boolean ACK; // ACK标志位
  private boolean SYN; // SYN标志位
  private boolean FIN; // FIN标志位
  private int[] checkSum; // 校验和
  private byte[] data; // 数据包

  public ACKCallBack getCallBack() {
    return callBack;
  }

  public void setCallBack(ACKCallBack callBack) {
    this.callBack = callBack;
  }

  public interface ACKCallBack {
    void success(UDPPacket data);
  }

  public UDPPacket(int seq) {
    this.seq = seq;
  }

  public boolean isValid() {
    // 检验校验和
    return true;
  }

  public byte[] getByte() {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
      out.writeObject(this);
      out.flush();
      return bos.toByteArray();
    } catch (IOException e) {
      System.out.println("Error: Can't convert the packet to string.");
    }
    return null;
  }

  public static UDPPacket ReadByte(byte[] data) {
    try {
      return (UDPPacket) (new ObjectInputStream(new ByteArrayInputStream(data))).readObject();
    } catch (IOException | ClassNotFoundException e) {
      e.printStackTrace();
    }
    return null;
  }

  public DatagramPacket getPacket() {
    return packet;
  }

  public void setPacket(DatagramPacket packet) {
    this.packet = packet;
  }

  public byte[] getData() {
    return data;
  }

  public void setData(byte[] data) {
    this.data = data;
  }

  public int getAck() {
    return ack;
  }

  public void setAck(int ack) {
    this.ACK = true;
    this.ack = ack;
  }

  public int getSeq() {
    return seq;
  }

  public void setSeq(int seq) {
    this.seq = seq;
  }

  public boolean isACK() {
    return ACK;
  }

  public void setACK(boolean ACK) {
    this.ACK = ACK;
  }

  public boolean isSYN() {
    return SYN;
  }

  public void setSYN(boolean SYN) {
    this.SYN = SYN;
  }

  public boolean isFIN() {
    return FIN;
  }

  public void setFIN(boolean FIN) {
    this.FIN = FIN;
  }

  public int getWinSize() {
    return winSize;
  }

  public void setWinSize(int winSize) {
    this.winSize = winSize;
  }

  public int[] getCheckSum() {
    return checkSum;
  }

  public void setCheckSum(int[] checkSum) {
    this.checkSum = checkSum;
  }
}