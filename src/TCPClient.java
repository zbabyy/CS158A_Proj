/*
Revanth Cherukuri
Zaria Baker
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.IntStream;

//Client class
public class TCPClient {
    static int missingLower = 0;
    static int missingUpper = 0;

    //flag to indicate there was segment loss
    static boolean loss = false;

    //to keep track of the first (oldest) segment that went missing
    static int missingSegment = 0;

    //tracking the current segment being transmitted
    static int segment = 1;

    //flag to determine if window was already divided in two
    static boolean halved = false;

    //arraylist to keep track of dropped segments for graph
    static ArrayList<HashMap<Integer, Integer>> seqNumsDropped = new ArrayList<>();

    //arraylist to keep track of window sizes on client size for graph
    static ArrayList<Integer> windowSizes = new ArrayList<>();
    static Random rand = new Random();

    //constant for segment size (no. of sequence numbers)
    static int SEGMENT_SIZE = 1024;

    //constant for total number of segments being transmitted
    static int TOTAL_SEGMENTS = 10000000;

    //constant for max size window can reach, 2^16
    static int MAX_WINDOW_SIZE = (int) Math.pow(2, 16);

    //constant for timeout (time ot wait for ACK, before establishing the segment never received was dropped)
    static int TIMEOUT = 50;   // milli secs

    static int windowSize = 0;
    public static void main(String[] args) {
        //local IP address of machine
        String serverIp = "192.168.4.102";
        int serverPort = 12345;

        String initialString = "network";

        try {
            //opening TCP sockets for communication to server
            Socket clientSocket = new Socket(serverIp, serverPort);
            clientSocket.setSoTimeout(TIMEOUT);
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            DataInputStream in = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));

            System.out.println("Sending initial string: " + initialString);

            //sending the initial string to the server
            out.println(initialString);

            //reading the response from the server
            String successResponse = in.readUTF();
            System.out.println("Received success response: " + successResponse);

            // start with window size as 1 segment
            windowSize = SEGMENT_SIZE;

            //loop goes as long as the number of segments is within the total amount being transmitted
            while (segment < TOTAL_SEGMENTS + 1) {
                //number of segments in current window
                int segmentCount = windowSize / SEGMENT_SIZE;

                //first sequence number in segment
                int innerBound = 0;

                //last sequence number in segment
                int outerBound = 0;

                // send segments
                for (int i = 0; i < segmentCount; i++) {
                    //setting values for sequence number bounds in segment
                    innerBound = i * SEGMENT_SIZE + 1;
                    outerBound = (i + 1) * SEGMENT_SIZE;

                    System.out.println("Sending segment: " + segment + ": " + innerBound + " - " + outerBound + " WINDOW SIZE: " + windowSize);

                    //temporary array representing the segment from innerBound to upperBound (first and last sequence numbers in segment)
                    int [] tempSegment = IntStream.rangeClosed(innerBound, outerBound).toArray();

                    // simulate a loss randomly
                    int randInt = rand.nextInt(10000 - 1 + 1) + 1;

                    // make sure to send the first and last segments, and simulating a small percentage of random segment drops
                    if ((randInt % 13 != 0 || randInt % 12 != 0) || (segment == 1 || segment == TOTAL_SEGMENTS)) {
                        writeInts(out, tempSegment);
                    }

                    int ack = waitForACK(in);
                    if (ack == 0) {
                        //setting this flag to true to indicate there was segment loss
                        loss = true;

                        //setting this flag to false, because we need to half this window as the current segment was lost
                        halved = false;

                        //keeping track seq number bounds for the missing segment
                        if (missingLower == 0) {
                            missingLower = (int) innerBound;
                            missingUpper = (int) outerBound;

                            //setting the oldest missing segment, by making sure it has not been set before with this check
                            missingSegment = segment;
                        }
                        HashMap<Integer, Integer> droppedMap = new HashMap<>();

                        //storing the dropped seq number and the segment it was dropped at, for graphing
                        droppedMap.put(segment, outerBound);
                        seqNumsDropped.add(droppedMap);
                        System.out.println("Missing ACK for segment: " + segment + " Missed: " + seqNumsDropped.size());
                    }

                    //storing window sizes in client for sent window size graph
                    if (segment == 1 || (segment % 1000 == 0)) {
                        windowSizes.add(windowSize);
                    }

                    //increment number of segments
                    segment++;

                    //reached 10 million
                    if (segment >= TOTAL_SEGMENTS + 1) {
                        break;
                    }
                }

                //same logic as in server, if there is no loss so far, check if the window has not reached max value of 2^16, to increase it by double
                if (loss == false) {
                    if (windowSize < MAX_WINDOW_SIZE) {
                        windowSize *= 2;
                    }
                }

                //if loss has been detected, check if the window has already been halved. If not, half it and set that flag to true, means current segment was lost
                else {
                    if (!halved) {
                        windowSize = (windowSize/2) - (windowSize % 1024);
                        halved = true;
                    }

                    //if loss has been detected and window was halved, check if it is not at max value 2^16 before incrementing window size by 1 segment, this means this current segment wasn't lost but there has been loss before
                    else {
                        if (1 + (outerBound - innerBound) <  MAX_WINDOW_SIZE) {
                            windowSize += SEGMENT_SIZE;
                        }
                    }
                }

                if (segment >= TOTAL_SEGMENTS + 1) {
                    break;
                }
            }

            if (segment % 1000 == 0) {
                windowSizes.add(windowSize);
            }

            //same as in server, here the oldest missing segment is saved, and is retransmitted at the end
            if (missingLower != 0) {
                System.out.println("Re-sending oldest missing segment (" + missingSegment + "): " + missingLower + " - " + missingUpper);

                //the sequence number is re-sent to the server
                int [] tempSegment = IntStream.rangeClosed(missingLower, missingUpper).toArray();
                writeInts(out, tempSegment);

                //method is called again to wait for ACK from server
                waitForACK(in);
            }

            //closing the socket
            clientSocket.close();

            System.out.println("Segments dropped: " + seqNumsDropped.size());

            //passing array to method to create CSV file of dropped sequence numbers
            createDroppedSeqNumTable(seqNumsDropped);

            //passing array to method to create CSV file of send window sizes
            createSentWindowSizeTable(windowSizes);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    params: windowSizes: ArrayList<Integer>
    return: void
    creates CSV file of all the windowSizes at each segment
     */
    private static void createSentWindowSizeTable(ArrayList<Integer> windowSizes) {
        String csvPath = "sent-window-size-by-time.csv";
        try {
            FileWriter fw = new FileWriter(csvPath);
            BufferedWriter bw = new BufferedWriter(fw);

            bw.write("Segment,Window Size (Sent)");
            bw.newLine();

            for (int i = 0; i < windowSizes.size(); i++) {
                bw.write((i == 0 ? 1 : i * 1000) + "," + windowSizes.get(i));
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
    params: hm: ArrayList<HashMap<Integer, Integer>>
    return: void
    creates CSV file of all sequence numbers dropped, used HashMap to store with segment where they were dropped
     */
    private static void createDroppedSeqNumTable(ArrayList<HashMap<Integer, Integer>> hm) {
        String csvPath = "seq-num-dropped-by-time.csv";
        try {
            FileWriter fw = new FileWriter(csvPath);
            BufferedWriter bw = new BufferedWriter(fw);

            bw.write("Segment,Sequence Number Dropped");
            bw.newLine();

            for (HashMap<Integer, Integer> hmap : hm) {
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
    instead of writing an entire segment array, write the segment number, the sequence number bounds (inner/outer), and the window size
     */
    private static void writeInts(PrintWriter out, int[] ints) throws IOException {
//        out.writeObject(ints);
        out.println(segment);
        out.println(ints[0]);
        out.println(ints[ints.length - 1]);
        out.println(windowSize);
    }

    /*
    params: in: DataInputStream
    return: ack: int
    this method handles receiving and returning acknowledgement from the server
     */
    private static int waitForACK(DataInputStream in)  {
        int ack = 0;
        try {
            //reading from server
            ack = in.readInt();
        }

        //if unable to read from server, means ACK wasn't received and current segment was lost
        catch (IOException e) {
            //System.out.println("Missing ACK");
        }
        return ack;
    }
}
