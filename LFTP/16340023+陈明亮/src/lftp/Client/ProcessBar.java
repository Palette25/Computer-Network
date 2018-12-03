package lftp.Client;

public class ProcessBar{
	private long startPoint;  // start point percent
	private long barSize;  // file size (bytes)
	private int barLength;  // bar length
	private String fileName;
	private String barInfo;
	private final int MAX_LENGTH = 50 * 1024;

	public ProcessBar(int start, long barSize, String fileName){
		this.startPoint = start;
		this.barSize = barSize;
		this.fileName = fileName;
		this.barLength = 30;
	}

	public void initBar(boolean flag){
		long packetNum = this.barSize / MAX_LENGTH;
		if(flag){
			System.out.printf("[Info]Sending file %s to LFTP-Server: ", fileName);
			this.barInfo = "[Info]Sending file to LFTP-Server: ";
		}else {
			System.out.printf("[Info]Getting file %s from LFTP-Server: ", fileName);
			this.barInfo = "[Info]Getting file from LFTP-Server: ";
		}
	}

	public String showBar(int sign){
		StringBuilder bar = new StringBuilder();
        bar.append("[");
        for (int i=1; i<=this.barLength; i++) {
            if (i < sign) {
                bar.append("-");
            } else if (i == sign) {
                bar.append(">");
            } else {
                bar.append(" ");
            }
        }
        bar.append("]");
        return bar.toString();
	}

	public void updateBar(int currPoint){
		double rate = (double)(currPoint - startPoint) / ((double)barSize / MAX_LENGTH);
		if(barSize < MAX_LENGTH && currPoint != startPoint)
			rate = 1;
		int sign = (int)(rate * barLength);

		System.out.print("\r");

		System.out.print(this.barInfo + showBar(sign) + String.format(" %d", (int)(rate*100)) + "%");
	}

};