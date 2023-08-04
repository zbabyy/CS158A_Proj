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
            //creating server socket
            ServerSocket serverSocket = new ServerSocket(portNumber);
            System.out.println("Server listening for incoming connections...");

            while (true) {
                Socket clientSocket = serverSocket.accept();

                //connecting to client via IP address
                System.out.println("Established connection successfully to: " + clientSocket.getInetAddress());

                //calling method to handle receiving segments from client
                handleClient(clientSocket);
                clientSocket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
    params: clientSocket: Socket
    return: void
    handles reception of messages from client
     */
    private static void handleClient(Socket clientSocket) throws IOException, ClassNotFoundException {

        //keeping track of windowSize as it changes, for graph
        ArrayList<Integer> windowSizes = new ArrayList<>();

        //keeping track of received sequence numbers, for graph
        ArrayList<Integer> seqNumsReceived = new ArrayList<>();

        //constant for segment size (no. of sequence numbers)
        int SEGMENT_SIZE = 1024;

        //constant for total number of segments being transmitted from client
        int TOTAL_ITERATIONS = 10000; //10000000;

        //constant for max size window can reach, 2^16
        int MAX_WINDOW_SIZE = (int) Math.pow(2, 16);

        //receiving messages from client
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

        //reading the initial string from the client, testing that the transmission is working
        byte[] bytes = new byte[7];
        String initialString = (String)in.readLine();
        System.out.println("Received initial string: " + initialString);
        System.out.println("Sending initial success response\n");

        //writing back to the client a success message
        out.writeUTF("Success");

        //count of segments received from client
        int receivedSegments = 0;

        //temp value for received count, used for goodput every 1000
        int tempReceivedSegments = 0;

        //count of segments dropped
        int missingSegments = 0;

        //temp value for dropped count, used for goodput every 1000
        int tempMissingSegments = 0;

        //segment counter
        int segmentCount = 0;
        int expectedSegment = 1;

        //ArrayList to store goodputs every 1000, for both averaging at the end and graph
        ArrayList<Double> goodput = new ArrayList<>();

        while (true) {
            //receiving the segment
            int[] segment = readInts(in);

            if (segment == null || segment.length == 0) {
                break;
            }

            //storing segment number being passed from client
            segmentCount = segment[0];

            //storing inner sequence number bound (first seq number in segment) being passed from client
            int receivedInnerbound = segment[1];

            //storing outer sequence number bound (last seq number in segment) being passed from client
            int receivedOuterbound = segment[2];

            //populating to array of received seq numbers, for graph
            seqNumsReceived.add(receivedOuterbound);

            //sending ACK back to client upon receiving segment, which is the received sequence number (last value in segment) + 1
            out.writeInt(receivedOuterbound + 1);

            //storing window size being passed from client
            int windowSize = segment[3];

            //keeping track of this value for goodput
            receivedSegments++;

            //needed for goodput, this value resets every 1000 so the goodputs being calculated are not combined with the previous 1000 segments
            tempReceivedSegments++;

            if (segmentCount != expectedSegment) {
                //keeping track of how many segments are missing.
                missingSegments += segmentCount - expectedSegment;

                //this value is used for goodput as well for the same reason as tempReceivedSegments
                tempMissingSegments += segmentCount - expectedSegment;
                System.out.println("Missed segment: " + expectedSegment + " Total Missed: " + missingSegments);
            }

            System.out.println("Received Segment: " + segmentCount + " Segment: " +
                        receivedInnerbound + " - " + receivedOuterbound + " Total Missed: " + missingSegments);

            expectedSegment = segmentCount + 1;

            //populating array of updated window size, for graph
            windowSizes.add(windowSize);

            //calculating the goodput every 1000 segments received
            if (segmentCount % 1000 == 0) {

                //goodput = received segments / sent segments, this is the same as the received segments / the received segments + missing segments.
                //(cont.)This is because the server will not have received the max amount of segments when accounting for loss.
                //(cont.)If the client doesn't receive ACK (segment was lost) it adjusts the window and sends it as the next segment. To the server, since it never received the first segment, it comes in place of the segment it initially was anticipating
                double goodPut = (double) tempReceivedSegments / (tempReceivedSegments + tempMissingSegments);
                tempReceivedSegments =  0;  //resetting this for the next 1000
                tempMissingSegments = 0;    //same
                System.out.println("Goodput after 1000 segments: " + goodPut);

                //adding goodput to arraylist so average can be calculated at the end
                goodput.add(goodPut);
            }

            //if all segments were sent
            if (segmentCount >= TOTAL_ITERATIONS) {
                System.out.println("All segments received: " + segmentCount);
                break;
            }
        }

        //client will resend the oldest missing segment, this is to handle that. First it checks if there were any missing segments
        if (missingSegments != 0) {

            //same logic for reading segment and sending ACK
            System.out.println("\nWaiting for missed segment");
            int[] missing = readInts(in);
            if (missing != null) {
                int receivedOuterbound = missing[2];
                out.writeInt((receivedOuterbound + 1));
                System.out.println("Received re-sent segment: " + missing[1] + " - " + receivedOuterbound);
                System.out.println("Sending ACK: " + (receivedOuterbound + 1) + "\n");
            }
        }

        //calling method to create csv file of all goodputs, for graph
        createGoodputTable(goodput);

        //calling method to calculate average goodput
        averageGoodput(goodput);
        System.out.println("Total Missing Segments: " + missingSegments);

        //calling method to create csv file for window size
        createWindowSizeTable(windowSizes);

        //calling method to create csv file for received sequence numbers
        createReceivedSeqNumTable(seqNumsReceived);
    }

    /*
    params: windowSizes: ArrayList<Integer>
    return: void
    creates CSV file of window sizes on server side by time (segment)
     */
    private static void createWindowSizeTable(ArrayList<Integer> windowSizes) {
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
    params: goodput: ArrayList<Double>
    return: void
    creates CSV file of calculated goodputs
     */
    private static void createGoodputTable(ArrayList<Double> goodput) {
        String csvPath = "goodputs.csv";
        try {
            FileWriter fw = new FileWriter(csvPath);
            BufferedWriter bw = new BufferedWriter(fw);

            bw.write("Range,Goodput");
            bw.newLine();
            int j = 1000;
            for (int i = 0; i < goodput.size(); i++) {
                bw.write(j + "," + goodput.get(i));
                j += 1000;
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
    params: goodput: ArrayList<Double>
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
    reading 4 values passed from client
     */
    private static int[] readInts(BufferedReader in) throws IOException, ClassNotFoundException {
//        List<Integer> ints = (List<Integer>)in.readObject();
        int[] ints = new int[4];
        ints[0] = Integer.parseInt(in.readLine());
        ints[1] = Integer.parseInt(in.readLine());
        ints[2] = Integer.parseInt(in.readLine());
        ints[3] = Integer.parseInt(in.readLine());
        return ints;
    }
}
