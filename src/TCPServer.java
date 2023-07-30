/*
Revanth Cherukuri
Zaria Baker
 */

import java.io.*;
import java.net.*;
import java.util.ArrayList;

//Server class
public class TCPServer {
    public static void main(String[] args) {
        int portNumber = 12345;

        try {
            ServerSocket serverSocket = new ServerSocket(portNumber);                                           //creating server socket
            System.out.println("Server listening for incoming connections...");

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

        ArrayList<Double> windowSizes = new ArrayList<>();                                         //keeping track of windowSize as it changes, for graph
        ArrayList<Integer> seqNumsReceived = new ArrayList<>();                                     //keeping track of received sequence numbers, for graph

        //constants for segment size (no. of sequence numbers) and total segments being transmitted
        int SEGMENT_SIZE = 1024;
        long TOTAL_SEGMENTS = 10000000;
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); //receiving messages from client
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);            //to write back to the client

        //reading the initial string from the client, testing that the transmission is working
        String initialString = in.readLine();
        System.out.println("Received initial string: " + initialString);
        System.out.println("Sending initial success response\n");
        out.println("Success");                                                                     //writing back to the client a success message

        int expectedSegment = 1;
        int receivedSegments = 0;
        int tempReceivedSegments = 0;
        int missingSegments = 0;
        int tempMissingSegments = 0;
        int receivedSeqNum = 0;

        ArrayList<Double> goodput = new ArrayList<>();
        while (true) {
            receivedSeqNum  = 0;

            int [] segment = readInts(in);                                                          //calling method to parse sequence number integers being passed from client

            if (segment == null || segment.length == 0) {
                break;
            }

            int receivedSegment = segment[0];
            receivedSeqNum = segment[segment.length - 1];                                           //received sequence number is last value in segment array (1024, 2048, 3072, 4096, etc.)
            seqNumsReceived.add(receivedSeqNum);                                                    //populating to array of received seq numbers, for graph

            out.println((receivedSeqNum + 1));                                                      //sending ACK back to client upon receiving segment, which is the received sequence number (last value in segment) + 1
            receivedSegments++;                                                                     //keeping track of this value for goodput
            tempReceivedSegments++;                                                                 //needed for goodput, this value resets every 1000 so the goodputs being calculated are not combined with the previous 1000 segments

            if (receivedSegment != expectedSegment) {                                               //the segment that was supposed to be received was lost
                System.out.println("Missed client's segment " + expectedSegment + " Received: " + receivedSegment);

                missingSegments += (receivedSegment - expectedSegment);                             //keeping track of how many segments are missing.
                tempMissingSegments++;                                                              //this value is used for goodput as well for the same reason as tempReceivedSegments
            }
            System.out.println("Received segment " + receivedSegment + ": 1 - " + receivedSeqNum + ", missing: " + missingSegments);    //incrementing segment counter value to keep track of how many segments are received
            expectedSegment = receivedSegment + 1;

            windowSizes.add((double)receivedSeqNum/SEGMENT_SIZE);                                                       //populating array of updated window size, for graph

            //calculating the goodput every 1000 segments received
            if (receivedSegment % 1000 == 0) {
                double goodPut = (double) tempReceivedSegments / (tempReceivedSegments + tempMissingSegments);      //goodput = received segments / sent segments, this is the same as the received segments / the received segments + missing segments.
                tempReceivedSegments =  0;  //resetting this for the next 1000                                              //(cont.)This is because the server will not have received the max amount of segments when accounting for loss.
                tempMissingSegments = 0;    //same                                                                          //(cont.)If the client doesn't receive ACK (segment was lost) it adjusts the window and sends it as the next segment. To the server, since it never received the first segment, it comes in place of the segment it initially was anticipating
                System.out.println("Goodput after 1000 segments: " + goodPut);                                              //(cont.)So at the end, if there were 200 total segments with 15 lost, the server will show it received 185 at the end. So the goodput would be the received / the total sent which includes those that were dropped, which is the sum of the received and the ones missing or dropped
                goodput.add(goodPut);                                                                               //adding goodput to arraylist so average can be calculated at the end
            }

            if (receivedSegment == TOTAL_SEGMENTS) {                                             //if all segments were sent
                break;
            }
        }

        missingSegments = (int)TOTAL_SEGMENTS - receivedSegments;

        if (missingSegments != 0) {                                                                                 //client will resend the oldest missing segment, this is to handle that. First it checks if there were any missing segments
            //same logic for reading segment and sending ACK
            System.out.println("\nWaiting for missed segment");
            String data = in.readLine();
            if (data != null) {
                receivedSeqNum = Integer.parseInt(data);
                System.out.println("Received re-sent segment: 1 - " + receivedSeqNum);
                System.out.println("Sending ACK: " + (receivedSeqNum + 1) + "\n");
                out.println((receivedSeqNum + 1));
            }
        }

        averageGoodput(goodput);    //calling method to calculate average goodput at the end
        System.out.println("Total Missing Segments: " + missingSegments);

        //calling methods to create CSV files for window sizes and received sequence numbers by segment
        createWindowSizeTable(windowSizes);
        createReceivedSeqNumTable(seqNumsReceived);
    }

    /*
    params: windowSizes: ArrayList<Double>
    return: void
    creates CSV file of window sizes on server side by time (segment)
     */
    private static void createWindowSizeTable(ArrayList<Double> windowSizes) {
        String csvPath = "received-window-size-by-time.csv";
        try {
            FileWriter fw = new FileWriter(csvPath);
            BufferedWriter bw = new BufferedWriter(fw);

            bw.write("Segment,Window Size (Received)");
            bw.newLine();

            for (int i = 0; i < windowSizes.size(); i++) {
                bw.write((i + 1) + "," + windowSizes.get(i));
                bw.newLine();
            }
            bw.close();
            fw.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    params: seqNumsReceived: ArrayList<Integer>
    return: void
    creates CSV file of sequence numbers received on server side per segment
     */
    private static void createReceivedSeqNumTable(ArrayList<Integer> seqNumsReceived) {
        String csvPath = "seq-num-received-by-time.csv";
        try {
            FileWriter fw = new FileWriter(csvPath);
            BufferedWriter bw = new BufferedWriter(fw);

            bw.write("Segment,Sequence Number Received");
            bw.newLine();

            for (int i = 0; i < seqNumsReceived.size(); i++) {
                bw.write((i + 1) + "," + seqNumsReceived.get(i));
                bw.newLine();
            }
            bw.close();
            fw.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
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
    instead of reading an entire segment array, write only the boundaries for faster execution to server
     */
    private static int[] readInts(BufferedReader in) throws IOException {
        int[] ints = new int[3];
        ints[0] = Integer.parseInt(in.readLine());  // segment number
        ints[1] = Integer.parseInt(in.readLine());  // lower bound
        ints[2] = Integer.parseInt(in.readLine());  // upper bound
        return ints;
    }

}
