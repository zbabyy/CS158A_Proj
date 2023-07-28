import java.io.*;
import java.net.*;

public class TCPServer {
    public static void main(String[] args) {
        int portNumber = 12345;

        try {
            ServerSocket serverSocket = new ServerSocket(portNumber);
            System.out.println("Server is listening for incoming connections...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connection established with: " + clientSocket.getInetAddress());

                handleClient(clientSocket);
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) throws IOException {
        int SEGMENT_SIZE = 1024;
        long TOTAL_SEGMENTS = 10000000;
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

        // Reading initial string from the client
        String initialString = in.readLine();
        System.out.println("Received initial string: " + initialString);
        System.out.println("Sending initial success response\n");
        out.println("Success");

        int expectedSeqNum = 0;
        int expectedSegment = 1;
        int slidingWindow = 1; // Initialize sliding window to 1 segment (1024 sequence numbers)
        int receivedSegments = 0;
        int sentSegments = 0;
        int missingSegments = 0;
        int receivedSeqNum = 0;
        int segmentCounter = 1;
        while (true) {
            expectedSeqNum = expectedSegment * SEGMENT_SIZE;
            for (int i = 0; i < expectedSeqNum; i++) {
                String data = in.readLine();
                if (data == null) {
                    break;
                }
                receivedSeqNum = Integer.parseInt(data);
//                if (receivedSeqNum != i + 1) {
//                    missing
//                }
            }

//            if (i == outerBound) {
//                System.out.println("Received segment: " + expectedSegment + " - " + expectedSeqNum);
//                out.println(outerBound + 1);
//            }

            if (receivedSeqNum == expectedSeqNum) {
                out.println("ACK " + (expectedSeqNum + 1));
                System.out.println("Received segment " + segmentCounter++ + ": 1 - " + expectedSeqNum);
                if (expectedSeqNum < (long) Math.pow(2, 16)) {
                    expectedSegment *= 2;
                }
                receivedSegments++;
                sentSegments++;
            } else {
                // Simulate segment loss, not acknowledging and waiting for retransmission
                out.println("ACK " + expectedSeqNum);
                missingSegments++;
            }

            // Calculate good-put and report every 1000 segments received
            if (receivedSegments % 1000 == 0) {
                double goodPut = (double) receivedSegments / sentSegments;
                System.out.println("Good-put after " + receivedSegments + " segments: " + goodPut);
            }

            if (receivedSegments == TOTAL_SEGMENTS) {
                break;
            }
        }

        // Calculate and report average good-put
        double averageGoodPut = (double) receivedSegments / sentSegments;
        System.out.println("Average Good-put: " + averageGoodPut);
        System.out.println("Total Missing Segments: " + missingSegments);
    }

    private static int adjustSlidingWindow(int windowSize, boolean acked) {
        if (acked) {
            if (windowSize < 65536) {
                windowSize = Math.min(windowSize * 2, 65536); // Maximum sliding window size is 2^16
            } else {
                windowSize = 65536; // Maintain the maximum value
            }
        } else {
            windowSize = Math.max(windowSize / 2, 1); // Reduce sliding window to half on segment loss
        }
        return windowSize;
    }
}
