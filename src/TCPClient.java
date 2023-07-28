import java.io.*;
import java.net.*;
import java.util.stream.IntStream;

public class TCPClient {
    public static void main(String[] args) {
        String serverIp = "192.168.4.102";
        int serverPort = 12345;

        String initialString = "network";
        int SEGMENT_SIZE = 1024;

        try {
            Socket clientSocket = new Socket(serverIp, serverPort);
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Sending initial string
            System.out.println("Sending initial string: " + initialString);
            out.println(initialString);
            String successResponse = in.readLine();
            System.out.println("Received success response: " + successResponse);

            int slidingWindow = 1; // Initialize sliding window to 1 segment (1024 sequence numbers)
//            int sequenceNumber = 0;
            int [] sequenceNumber = IntStream.rangeClosed(1, 1024).toArray();
            int TOTAL_SEGMENTS = 65; // Number of segments to send
            boolean lostSegment = false;
            int windowSize = 1;
            long innerBound = 1;
            long outerBound = 0;
            int segmentCounter = 1;
            while (segmentCounter < TOTAL_SEGMENTS + 1) {
                outerBound = windowSize * SEGMENT_SIZE;
                System.out.println("Sending segment: " + segmentCounter + ": " + innerBound + " - " + outerBound);

                int [] tempSegment = IntStream.rangeClosed((int)innerBound, (int)outerBound).toArray();
                for (int i = 0; i < tempSegment.length; i++) {
                    out.println(tempSegment[i]);
                }
                String ack = in.readLine();
                System.out.println("ack: " + ack);

//                if (ack.startsWith("ACK")) {
//                    int ackedSeqNum = Integer.parseInt(ack.substring(4).trim());
//                    if (ackedSeqNum == sequenceNumber + 1) {
//                        slidingWindow = adjustSlidingWindow(slidingWindow, true, lostSegment);
//                        sequenceNumber++;
//                        lostSegment = false;
//                    } else {
//                        slidingWindow = adjustSlidingWindow(slidingWindow, false, lostSegment);
//                        lostSegment = true;
//                    }
//                }

//                System.out.println("Sent: " + sequenceNumber + " Sliding window: " + slidingWindow);


                if (1 + (outerBound - innerBound) < (long) Math.pow(2, 16)) {
                    windowSize *= 2;
                }

                segmentCounter ++;
            }

            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int log2(int x) {
        return (int) (Math.log(x) / Math.log(2));
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
