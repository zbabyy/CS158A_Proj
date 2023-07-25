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
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

        // Reading initial string from the client
        String initialString = in.readLine();
        System.out.println("Received initial string: " + initialString);

        int expectedSeqNum = 0;
        int slidingWindow = 1; // Initialize sliding window to 1 segment (1024 sequence numbers)
        int receivedSegments = 0;
        int sentSegments = 0;
        int missingSegments = 0;
        while (true) {
            String data = in.readLine();
            if (data == null) {
                break;
            }

            int receivedSeqNum = Integer.parseInt(data);
            if (receivedSeqNum == expectedSeqNum) {
                out.println("ACK " + (expectedSeqNum + 1));
                expectedSeqNum++;
                slidingWindow = adjustSlidingWindow(slidingWindow, true);
                receivedSegments++;
                sentSegments++;
            } else {
                // Simulate segment loss, not acknowledging and waiting for retransmission
                out.println("ACK " + expectedSeqNum);
                slidingWindow = adjustSlidingWindow(slidingWindow, false);
                missingSegments++;
            }

            System.out.println("Received: " + receivedSeqNum + " Sent: " + expectedSeqNum + " Sliding window: " + slidingWindow);

            // Calculate good-put and report every 1000 segments received
            if (receivedSegments % 1000 == 0) {
                double goodPut = (double) receivedSegments / sentSegments;
                System.out.println("Good-put after " + receivedSegments + " segments: " + goodPut);
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
