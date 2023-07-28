import java.io.*;
import java.net.*;
import java.util.ArrayList;

//Server class
public class TCPServer {
    public static void main(String[] args) {
        int portNumber = 12345;

        try {
            ServerSocket serverSocket = new ServerSocket(portNumber);                                           //creating server socket
            System.out.println("Server is listening for incoming connections...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Established connection successfully to: " + clientSocket.getInetAddress()); //connecting to client via IP address
                handleClient(clientSocket);                                                                     //calling method to handle receiving segments from client
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) throws IOException {
        //constants for segment size (no. of sequence numbers) and total segments being transmitted
        int SEGMENT_SIZE = 1024;
        long TOTAL_SEGMENTS = 200;
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); //receiving messages from client
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);            //to write back to the client

        //reading the initial string from the client, testing that the transmission is working
        String initialString = in.readLine();
        System.out.println("Received initial string: " + initialString);
        System.out.println("Sending initial success response\n");
        out.println("Success");                                                                     //writing back to the client a success message

        int expectedSeqNum = 0;
        double expectedSegment = 1;
        int receivedSegments = 0;
        int tempReceivedSegments = 0;
        int missingSegments = 0;
        int tempMissingSegments = 0;
        int receivedSeqNum = 0;
        int segmentCounter = 1;
        boolean loss = false;
        ArrayList<Double> goodput = new ArrayList<>();
        while (true) {
            expectedSeqNum = (int) (expectedSegment * SEGMENT_SIZE);                                //calculating the new expected sequence number based on the expected segment number. (segment 3 * 1024 = 3072 -> expected sequence number as it is the last in segment 3's window)
            receivedSeqNum  = 0;

            int [] segment = readInts(in);                                                          //calling method to parse sequence number integers being passed from client

            if (segment == null || segment.length == 0) {
                break;
            }
            receivedSeqNum = segment[segment.length - 1];                                           //received sequence number is last value in segment array (1024, 2048, 3072, 4096, etc.)
            System.out.println("Received segment " + segmentCounter++ + ": 1 - " + receivedSeqNum); //incrementing segment counter value to keep track of how many segments are received
            System.out.println("Sending ACK: " + (receivedSeqNum + 1) + "\n");
            out.println((receivedSeqNum + 1));                                                      //sending ACK back to client upon receiving segment, which is the received sequence number (last value in segment) + 1
            receivedSegments++;                                                                     //keeping track of this value for goodput
            tempReceivedSegments++;                                                                 //needed for goodput, this value resets every 1000 so the goodputs being calculated are not combined with the previous 1000 segments

            if (receivedSeqNum == expectedSeqNum) {                                                 //if the segment was received
                if (expectedSeqNum < (long) Math.pow(2, 16)) {                                      //if the sequence number in the segment is less than 2^16
                    if (!loss) {                                                                    //checking flag to determine if there has been any loss. If there hasn't, we can simply double the expected segment (window)
                        expectedSegment *= 2;
                    }
                    else {                                                                          //if there has been loss already, but not on this particular segment, we just increment by one
                        expectedSegment += 1;
                    }
                }
            } else {                                                                                //the segment that was supposed to be received was lost
                loss = true;                                                                        //setting the flag to true, to establish that there has been loss and we will not increase expected segment by double anymore
                System.out.println("Missed client's segment " + ": 1 - " + expectedSeqNum + "\n");
                expectedSegment /= 2;                                                               //dividing expected segment in half
                expectedSegment++;                                                                  //incrementing it by one
                missingSegments++;                                                                  //keeping track of how many segments are missing.
                tempMissingSegments++;                                                              //this value is used for goodput as well for the same reason as tempReceivedSegments
            }

            //calculating the goodput every 1000 segments received
            if (receivedSegments % 1000 == 0) {
                double goodPut = (double) tempReceivedSegments / (tempReceivedSegments + tempMissingSegments);      //goodput = received segments / sent segments, this is the same as the received segments / the received segments + missing segments.
                tempReceivedSegments =  0;  //resetting this for the next 1000                                              //(cont.)This is because the server will not have received the max amount of segments when accounting for loss.
                tempMissingSegments = 0;    //same                                                                          //(cont.)If the client doesn't receive ACK (segment was lost) it adjusts the window and sends it as the next segment. To the server, since it never received the first segment, it comes in place of the segment it initially was anticipating
                System.out.println("Goodput after 1000 segments: " + goodPut);                                              //(cont.)So at the end, if there were 200 total segments with 15 lost, the server will show it received 185 at the end. So the goodput would be the received / the total sent which includes those that were dropped, which is the sum of the received and the ones missing or dropped
                goodput.add(goodPut);                                                                               //adding goodput to arraylist so average can be calculated at the end
            }

            System.out.println("received: " + receivedSegments +  " missing: " + missingSegments + "\n");

            if (receivedSegments + missingSegments == TOTAL_SEGMENTS) {                                             //if all segments were sent
                break;
            }
        }

        if (missingSegments != 0) {                                                                                 //client will resend the oldest missing segment, this is to handle that. First it checks if there were any missing segments
            //same logic for reading segment and sending ACK
            String data = in.readLine();
            if (data != null) {
                receivedSeqNum = Integer.parseInt(data);
                System.out.println("Received re-sent segment: 1 - " + receivedSeqNum);
                System.out.println("Sending ACK: " + (receivedSeqNum + 1) + "\n");
                out.println((receivedSeqNum + 1));
            }
        }


//        double averageGoodPut = 0;
//        double goodPutSum = 0;
//        for (int i = 0; i < goodput.size(); i++) {
//            goodPutSum += goodput.get(i);
//        }
//        averageGoodPut = goodPutSum / goodput.size();
//        System.out.println("Average Goodput: " + averageGoodPut);
        averageGoodput(goodput);    //calling method to calculate average goodput at the end
        System.out.println("Total Missing Segments: " + missingSegments);
    }

    /*
    params: goodput: ArrayList
    returns: void
    iterates through stored goodputs calculated at each range of 1000 segments, and finds the average
     */
    private static void averageGoodput(ArrayList<Double> goodput) {
        double averageGoodPut = 0;
        double goodPutSum = 0;
        for (int i = 0; i < goodput.size(); i++) {
            goodPutSum += goodput.get(i);
        }
        averageGoodPut = goodPutSum / goodput.size();
        System.out.println("Average Goodput: " + averageGoodPut);
    }

    /*
    params: in: BufferedReader
    returns: int[]
    parses segment array passed from client and returns as integer array
     */
    private static int[] readInts(BufferedReader in) throws IOException {
        int val = Integer.parseInt(in.readLine());
        int[] ints = new int[val];
        for (int i = 0; i < ints.length; ++i) {
            ints[i] = Integer.parseInt(in.readLine());
        }
        return ints;
    }

}
