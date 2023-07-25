import java.io.*;
import java.net.*;

public class TCPClient {
    public static void main(String[] args) {
        String serverIp = "192.168.4.102";
        int serverPort = 12345;

        String initialString = "network";

        try {
            Socket clientSocket = new Socket(serverIp, serverPort);
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Sending initial string
            out.println(initialString);

            int slidingWindow = 1; // Initialize sliding window to 1 segment (1024 sequence numbers)
            int sequenceNumber = 0;
            int totalSegments = 10000000; // Number of segments to send
            boolean lostSegment = false;

            while (sequenceNumber < totalSegments) {
                out.println(sequenceNumber);
                String ack = in.readLine();

                if (ack.startsWith("ACK")) {
                    int ackedSeqNum = Integer.parseInt(ack.substring(4).trim());
                    if (ackedSeqNum == sequenceNumber + 1) {
                        slidingWindow = adjustSlidingWindow(slidingWindow, true, lostSegment);
                        sequenceNumber++;
                        lostSegment = false;
                    } else {
                        slidingWindow = adjustSlidingWindow(slidingWindow, false, lostSegment);
                        lostSegment = true;
                    }
                }

                System.out.println("Sent: " + sequenceNumber + " Sliding window: " + slidingWindow);
            }

            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int adjustSlidingWindow(int windowSize, boolean acked, boolean lostSegment) {
        if (acked) {
            if (windowSize < 65536) {
                windowSize = Math.min(windowSize * 2, 65536); // Maximum sliding window size is 2^16
            } else {
                windowSize = 65536; // Maintain the maximum value
            }
        } else {
            if (lostSegment) {
                windowSize = Math.max(windowSize / 2, 1); // Reduce sliding window to half on segment loss
            } else {
                windowSize = windowSize + 1; // Increase linearly by 1 segment on any successful transmission after segment loss
            }
        }
        return windowSize;
    }
}
