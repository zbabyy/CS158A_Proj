/*
Revanth Cherukuri
Zaria Baker
 */

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.stream.IntStream;

//Client class
public class TCPClient {
    static long outerBound = 0;                                                                             //last sequence number in segment
    static int missing = 0;                                                                                 //count of missing segments
    static boolean loss = false;                                                                            //flag to indicate there was segment loss
    static int missingSegment = 0;                                                                          //to keep track of the first (oldest) segment that went missing
    static int segmentCounter = 1;                                                                          //tracking the current segment being transmitted
    static boolean halved = false;                                                                          //flag to determine if window was already divided in two
    static ArrayList<HashMap<Integer, Long>> seqNumsDropped = new ArrayList<>();                            //arraylist to keep track of dropped segments for graph
    static ArrayList<Double> windowSizes = new ArrayList<>();                                               //arraylist to keep track of window sizes on client size for graph
    static Random rand = new Random();

    public static void main(String[] args) {
        String serverIp = "192.168.4.102";                                                                  //local IP address of machine
        int serverPort = 12345;

        String initialString = "network";
        //constant for segment size (no. of sequence numbers)
        int SEGMENT_SIZE = 1024;
        //constant for timeout (time ot wait for ACK, before establishing the segment never received was dropped)
        int TIMEOUT = 50;



        try {
            //opening TCP sockets for communication to server
            Socket clientSocket = new Socket(serverIp, serverPort);
            clientSocket.setSoTimeout(TIMEOUT);
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            System.out.println("Sending initial string: " + initialString);
            out.println(initialString);                                                                     //sending the initial string to the server
            String successResponse = in.readLine();                                                         //reading the response from the server
            System.out.println("Received success response: " + successResponse);

            int TOTAL_SEGMENTS = 10000000;                                                                  //constant for total number of segments being transmitted
            double windowSize = 1;
            long innerBound = 1;

            while (segmentCounter < TOTAL_SEGMENTS + 1) {                                                   //loop goes as long as the number of segments is within the total amount being transmitted
                outerBound = (int) (windowSize * SEGMENT_SIZE);
                System.out.println("Sending segment: " + segmentCounter + ": " + innerBound + " - " + outerBound);

                int [] tempSegment = IntStream.rangeClosed((int)innerBound, (int)outerBound).toArray();     //temporary array representing the segment from innerBound to upperBound (first and last sequence numbers in window)

                // simulate a loss randomly
                int randInt = rand.nextInt(10000 - 1 + 1) + 1;                                       //generating random number to simulate segment loss, number between 1 - 10000

                // make sure to send the first and last segments, and simulating a small percentage of random segment drops
                if ((randInt % 13 != 0 || randInt % 12 != 0) || (segmentCounter == 1 || segmentCounter == TOTAL_SEGMENTS)) {
                    writeInts(out, tempSegment);
                }

                waitForACK(in);                                                                             //calls method that waits for ACK and acts accordingly
                windowSizes.add(windowSize);                                                            //storing window sizes in client for sent window size graph
                if (!loss) {                                                                            //same logic as in server, if there is no loss so far, check if the window has not reached max value of 2^16, to increase it by double
//                    if (1 + (outerBound - innerBound) < (long) Math.pow(2, 16)) {
                    if (windowSize < 64) {
                        windowSize *= 2;
                    }
                }
                else {                                                                                  //if loss has been detected, check if the window has already been halved. If not, half it and set that flag to true, means current segment was lost
                    if (!halved) {
                        windowSize /= 2;
                        halved = true;

                        HashMap<Integer, Long> hm = new HashMap<>();
                        hm.put(segmentCounter, outerBound);
                        seqNumsDropped.add(hm);
                    }
                    else {                                                                              //if loss has been detected and window was halved, check if it is not at max value 2^16 before incrementing window size by 1, this means this current segment wasn't lost but there has been loss before
                        if (1 + (outerBound - innerBound) < (long) Math.pow(2, 16)) {
                            windowSize++;
                        }
                    }
                }

                segmentCounter++;                                                                          //increment number of segments
            }

            if (missing != 0) {                                                                             //same as in server, here the oldest missing segment and it's outer bound (last sequence number) are saved, and are retransmitted at the end
                System.out.println("Re-sending oldest missing segment (" + missingSegment + "): 1 - " + missing);
                out.println(missing);                                                                       //the sequence number is re-sent to the server
                waitForACK(in);                                                                             //method is called again to wait for ACK from server
            }

            clientSocket.close();                                                                           //closing the socket

            System.out.println("Segments dropped: " + seqNumsDropped.size());
            createDroppedSeqNumTable(seqNumsDropped);                                                       //passing array to method to create CSV file of dropped sequence numbers
            createSentWindowSizeTable(windowSizes);                                                         //passing array to method to create CSV file of send window sizes

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    params: windowSizes: ArrayList<Double>
    return: void
    creates CSV file of all the windowSizes at each segment
     */
    private static void createSentWindowSizeTable(ArrayList<Double> windowSizes) {
        String csvPath = "sent-window-size-by-time.csv";
        try {
            FileWriter fw = new FileWriter(csvPath);
            BufferedWriter bw = new BufferedWriter(fw);

            bw.write("Segment,Window Size (Sent)");
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
    params: hm: ArrayList<HashMap<Integer, Long>>
    return: void
    creates CSV file of all sequence numbers dropped, used HashMap to store with segment where they were dropped
     */
    private static void createDroppedSeqNumTable(ArrayList<HashMap<Integer, Long>> hm) {
        String csvPath = "seq-num-dropped-by-time.csv";
        try {
            FileWriter fw = new FileWriter(csvPath);
            BufferedWriter bw = new BufferedWriter(fw);

            bw.write("Segment,Sequence Number Dropped");
            bw.newLine();

            for (HashMap<Integer, Long> hmap : hm) {
                for (Integer key : hmap.keySet()) {
                    bw.write(key + "," + hmap.get(key));
                    bw.newLine();
                }
            }
            bw.close();
            fw.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    params: out: PrintWriter, ints: int[]
    return: void
    instead of writing an entire segment array, write only the boundaries for faster execution to the server
     */
    private static void writeInts(PrintWriter out, int[] ints) throws IOException {
        out.println(segmentCounter);
        out.println(ints[0]);
        out.println(ints[ints.length - 1]);
    }

    /*
    params: in: BufferedReader
    return: void
    this method handles receiving acknowledgement from the server
     */
    private static void waitForACK(BufferedReader in)  {
        try {
            String ack = in.readLine();                         //reading from server
        }
        catch (IOException e) {                                 //if unable to read from server, means ACK wasn't received and current segment was lost
            loss = true;                                        //setting this flag to true to indicate there was segment loss
            System.out.println("Missing ACK");
            halved = false;                                     //setting this flag to false, because we need to half this window as the current segment was lost
            if (missing == 0) {                                 //setting the oldest missing segment, by making sure it has not been set before with this check
                missing = (int) outerBound;
                missingSegment = segmentCounter;
            }
        }
    }

}
