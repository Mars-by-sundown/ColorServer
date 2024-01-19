/*
Name: Nicholas Ragano

Date: 2024-01-19

Java version: 21.0.1

Command-line compilation example:
    >javac ColorServer.java

To Start the server:
    >java ColorServer

To Start a client(can be more than one, each requires their own terminal window):
    >java ColorClient

Files needed: 
    ColorServer.java

7. Notes: 

    I have added functionality to keep a simple ledger of color transactions made, it is shown when a client uses the quit command after a transaction
    
--------------------

Thanks:

https://www.comrevo.com/2019/07/Sending-objects-over-sockets-Java-example-How-to-send-serialized-object-over-network-in-Java.html (Code dated 2019-07-09, by Ramesh)
https://rollbar.com/blog/java-socketexception/#
Also: Hughes, Shoffner and Winslow for Inet code.

--------------------
 */

 import java.io.*;
 import java.net.*;
 import java.util.Scanner;
 import java.util.ArrayList;


class ColorClient {

    private static int clientColorCount = 0;
    //MODIFICATION: will track all colors sent and received by client
    //  uses an ArrayList to hold Arrays of strings, a new Array will be added to the ledger after each transaction
    private static ArrayList<String[]> colorLedger = new ArrayList<String[]>();
    public static void main(String argv[]) {
        ColorClient cc = new ColorClient(argv);
        cc.run(argv);
    }

    //we dont really do anything with the constructor in this case
    public ColorClient(String argv[]) {
        System.out.println("\nThis is the Constructor\n");
    }

    public void run(String argv[]) {
        String serverName; //allows us to hold the IP of our server
        
        //get the server name or address we were passed
        if(argv.length < 1) {
            serverName = "localhost";
        }else{
            serverName = argv[0];
        }

        //Scanner Object to allow input
        Scanner consoleIn = new Scanner(System.in);

        //Get the name of the user. store it and greet them
        System.out.println("Enter your name: ");
        System.out.flush();
        String userName = consoleIn.nextLine();
        System.out.println("Hi " + userName);

        //get color loop, this takes new color requests until quit is typed in
        String colorFromClient = "";
        do{
            System.out.println("Enter a color, or type quit to end: ");
            colorFromClient = consoleIn.nextLine();
            if(colorFromClient.indexOf("quit") < 0){
                getColor(userName, colorFromClient, serverName);
            }
        }while (colorFromClient.indexOf("quit") < 0);
        consoleIn.close(); //Added this as it was not closed in the original ColorClient code
        System.out.println("Cancelled by user request.");
        System.out.println(userName + ", You completed " + clientColorCount + " color transactions");

        //Added this functionality to display additional information about the colors sent and received
        for(String[] s: colorLedger){
            System.out.println("Transaction: " + (colorLedger.indexOf(s) + 1) + ", Color Sent: " + s[0] + ", Color Received: " + s[1]);
        }
    }


    void getColor(String userName, String colorFromClient, String serverName){
        try{

            //create a ColorData object to pass the data to and from the server
            ColorData colorObj = new ColorData();
            colorObj.userName = userName;
            colorObj.colorSentFromClient = colorFromClient;
            colorObj.colorCount = clientColorCount;

            //Establish a connection to the server
            Socket socket = new Socket(serverName, 45565);
            System.out.println("\nWe have successfully connected to the ColorServer at port 45565");

            //Establish an output stream using our socket
            OutputStream outStream = socket.getOutputStream();
            ObjectOutputStream objectOutStream = new ObjectOutputStream(outStream);

            //Serializes and sends the colordata object to the server
            objectOutStream.writeObject(colorObj);
            System.out.println("We have sent the serialized values to the ColorServer's server socket");

            //Establish an input stream to listen for a response from the server
            InputStream inStream = socket.getInputStream();
            ObjectInputStream objectInStream = new ObjectInputStream(inStream);

            //Read the serialized ColorData response sent by the ColorServer
            ColorData inObject = (ColorData) objectInStream.readObject();

            //Record our colorcount in our client instance
            clientColorCount = inObject.colorCount;

            //The colorRecordPair is how the colorLedger is storing each transactions color exchange data for recall later
            String[] colorRecordPair = new String[2];
            colorRecordPair[0] = inObject.colorSentFromClient;
            colorRecordPair[1] = inObject.colorSentFromServer;

            //Adding the pair to our ledger
            colorLedger.add(colorRecordPair);

            //Give the user some information about what is going on and what the server said
            System.out.println("\nFROM THE SERVER:");
            System.out.println(inObject.messageToClient);
            System.out.println("The color sent back is: " + inObject.colorSentFromServer);
            System.out.println("The color count is: " + inObject.colorCount + "\n");

            //Be sure to close our connection out when we are done
            System.out.println("Closing the connection to the ColorServer.\n");
            socket.close();

        }catch(ConnectException CE){
            //Will be thrown if server is not open, also thrown if request is refused (server backlog queue is exceeded)
            System.out.println("\nOh no. The ColorServer refused our connection! Is it running?\n");
            CE.printStackTrace();

        }catch(UnknownHostException UH){
            //Thrown when IP address of the host could not be determined, possibly isnt online
            System.out.println("\nUnknown Host problem.\n"); 
            UH.printStackTrace();

        }catch(ClassNotFoundException CNF){
            //This will be thrown if there is an issue with the Serialized ColorData class we are passing back and forth
            CNF.printStackTrace();

        }catch(IOException IOE){
            IOE.printStackTrace(); 
        }
    }
}

class ColorData implements Serializable {
    //Allows this data to be serialized so that it can used by the object stream and sent over network
    String userName;
    String colorSentFromClient;
    String colorSentFromServer;
    String messageToClient;
    int colorCount;
}

class ColorWorker extends Thread {
    Socket sock;

    //Constructor
    ColorWorker(Socket s){
        //Takes an arg of socket type and assigns it to local ColorWorker member, sock
        sock = s;
    }

    public void run(){
        try{
            //Creates an Object input stream allowing us to read in data
            InputStream InStream = sock.getInputStream();
            ObjectInputStream ObjectInStream = new ObjectInputStream(InStream);

            //Responsible for reading the incoming serialized data and reconstructing the Colordata object
            ColorData InObject = (ColorData) ObjectInStream.readObject();

            //Set up and prepare our output stream so we can send data back to the client
            OutputStream outStream = sock.getOutputStream();
            ObjectOutputStream objectOutStream = new ObjectOutputStream(outStream);

            //Printing some info in the cmdline of the server
            System.out.println("\nFROM THE CLIENT:\n");
            System.out.println("Username: " + InObject.userName);
            System.out.println("Color sent from the client: " + InObject.colorSentFromClient);
            System.out.println("Connections count (State!): " + (InObject.colorCount + 1));

            //This is where we are getting a random color to send back to the client and adding to your count
            InObject.colorSentFromServer = getRandomColor();
            InObject.colorCount++;
            InObject.messageToClient = 
                String.format("Thanks %s for sending the color %s", InObject.userName, InObject.colorSentFromClient);

            //Serialize our ColorData Object with the updated data and send it back to the client
            objectOutStream.writeObject(InObject);

            System.out.println("Closing the client socket connection...");
            sock.close(); //close the connection, we are done

        } catch(ClassNotFoundException CNF){
            //This will be thrown if there is an issue with the Serialized ColorData class we are passing back and forth
            CNF.printStackTrace();
        } catch( IOException x){
            System.out.println("Server error.");
            x.printStackTrace();
        }
    }

    //A little function to handle the randomized color selection
    String getRandomColor(){
        String[] colorArray = new String[]
        {
            "Red","Blue","Green","Yellow", "Magenta", "Silver", "Aqua", "Gray", "Peach", "Orange"
        };
        int randomArrayIndex = (int) (Math.random() * colorArray.length);
        return (colorArray[randomArrayIndex]);
    }
} 

public class ColorServer {
    public static void main(String[] args) throws Exception {
        int q_len = 6; /*Maximum number of requests to queue in the backlog, additional requests will be refused if full */
        int serverPort = 45565;
        Socket sock;

        //split into two lines because I think it looks nicer
        System.out.println("Nicholas Ragano's Color Server 1.0 starting, Listening on port " + serverPort + ".\n");

        //Create our server socket using our port and allowed queue length
        ServerSocket serverSock = new ServerSocket(serverPort, q_len);
        System.out.println("Server open and awaiting connections...");

        while(true){
            //Listen until a request comes in, accept it and spin up a worker thread to handle it, then return to listening
            sock = serverSock.accept(); //accept creates a new Socket and returns it
            System.out.println("Connection from: " + sock);
            new ColorWorker(sock).start(); //this is the thread being started and sent off to do its own thing
        }
    }
}
/*
------------------------------------ OUTPUT ------------------------------------ 

SERVER:

>java ColorServer
Nicholas Ragano's Color Server 1.0 starting, Listening on port 45565.

Server open and awaiting connections...
Connection from: Socket[addr=/127.0.0.1,port=61646,localport=45565]

FROM THE CLIENT:

Username: Nick
Color sent from the client: Red
Connections count (State!): 1
Closing the client socket connection...
Connection from: Socket[addr=/127.0.0.1,port=61654,localport=45565]

FROM THE CLIENT:

Username: Nick
Color sent from the client: Blue
Connections count (State!): 2
Closing the client socket connection...

--------------------------------------------------------
CLIENT:

>java ColorClient

This is the Constructor

Enter your name: 
Nick
Hi Nick
Enter a color, or type quit to end: 
Red

We have successfully connected to the ColorServer at port 45565
We have sent the serialized values to the ColorServer's server socket

FROM THE SERVER:
Thanks Nick for sending the color Red
The color sent back is: Green
The color count is: 1

Closing the connection to the ColorServer.

Enter a color, or type quit to end:
Blue

We have successfully connected to the ColorServer at port 45565
We have sent the serialized values to the ColorServer's server socket

FROM THE SERVER:
Thanks Nick for sending the color Blue
The color sent back is: Aqua
The color count is: 2

Closing the connection to the ColorServer.

Enter a color, or type quit to end:
quit
Cancelled by user request.
Nick, You completed 2 color transactions
Transaction: 1, Color Sent: Red, Color Received: Green
Transaction: 2, Color Sent: Blue, Color Received: Aqua


----------------------------------------------------------


MY D2L COLORSERVER DISCUSSION FORUM POSTINGS:

Reply to a post by Syed Saifuddin:
    I believe you are encouraged to do so if it helps your understanding. 
    So long as the code compiles in the same way and the functionality does not 
    change then the variable names can be whatever helps you understand the best. 
    I have changed my variable names to help make it easier for me to understand what is occuring

Reply to Robinkumar Ramanbhai:
    I did as was suggested and rewrote each and every line by hand and found myself 
    changing some variable/ Parameter names to make it easier for me to read and understand. 
    So long as the program compiles and runs the same I dont think this will be an issue. 
    As was suggested in another comment chain in the ColorServer discussion forum 
    I would note this on the checklist where applicable.

Reply to Yax Nileshbhai:
    I believe its stated in a few places that for this assignment the TII plagiarism score does not apply 
    as the assignment is to essentially read through (and write) each line of code, and adjust the comments. 
    So assuming you do that the code itself will not change. as long as you rewrite the comments to show you 
    have an understanding of the code then you should be fine.

Reply to Michael Placzek:
Its optional so no you dont need to do those things, I would just mark no for that item if you didnt do anything that applies.

 */