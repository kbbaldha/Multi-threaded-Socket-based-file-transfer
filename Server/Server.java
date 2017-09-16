/**
 * Created by chiranth on 11/12/2016.
 */


import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

public class Server {

    private static final int sPort = 8000;   //The server will be listening on this port number
    private Socket clientsocket; //this is the socket which is used to store the connection information for the client
    private static HashMap<String,ObjectOutputStream> group=new HashMap<String,ObjectOutputStream>();//This is used to store the user and their corresponding socket writers

    public static void main(String[] args) throws Exception {
        System.out.println("The server is running.\n");
        ServerSocket listener = new ServerSocket(sPort);

        int clientNum = 1;//this is to check the number of clients connected to the server once it started listening for connections
        try {
            while(true) {
                Socket clientsocket=listener.accept(); //keep listening for new connection requests
                new Handler(clientsocket,clientNum).start(); //create and start a new thread for each client socket connection
                System.out.println("Client "  + clientNum + " is connected!\n");
                clientNum++;
            }
        } finally {
            listener.close();//close the listener
        }

    }

    /**
     * A handler thread class.  Handlers are spawned from the listening
     * loop and are responsible for dealing with a single client's requests.
     */
    private static class Handler extends Thread
    {
        private String message;    //message received from the client
        private Socket connection;  // This is the reliable socket with which server and client interact
        private ObjectInputStream in;	//stream read from the socket
        private ObjectOutputStream out;//stream write to the socket
        private int no;		//The index number of the client
        private String clientname; // This is the name of the connection or the user connecting to the server
        FileOutputStream fos;   //This is used to save the file received from client
        BufferedOutputStream bos;
        int bytesRead,current=0;
        FileInputStream fis;
        BufferedInputStream bis; //Input stream to read from the file

        //constructor to initialize the socket
        public Handler(Socket connection, int no) {
            this.connection = connection;
            this.no = no;
        }

        //Method for the thread run
        public void run() {
            try{
                //initialize Input and Output streams
                out = new ObjectOutputStream(connection.getOutputStream());
                out.flush();
                in = new ObjectInputStream(connection.getInputStream());
                try{
                    while(true)
                    {
                        sendMessage("Submit Name"); // Specify the client to set a username
                        message = (String)in.readObject();
                        if(!group.containsKey(message)) //Check if the username is available
                        {
                            group.put(message,out); //Add the username and corresponding socket writer to the hashmap
                            clientname=message;
                            sendMessage("successfully registered "+message);//Acknowledge if username is available
                            break;
                        }

                        sendMessage("Try Different Name");//Communicate if username is already taken
                    }
                }
                catch(ClassNotFoundException classnot){
                    System.err.println("Data received in unknown format");
                }
                try
                {
                    while(true)
                    {
                        //receive the message sent from the client
                        message = (String)in.readObject();
                        //show the message to the user
                        System.out.println("Receive message: " + message + " from client " + clientname+"\n");
                        //Fetch the command from the message which is sepertated by '#'
                        String command = getCommand(message);

                        switch(command.toLowerCase())
                        {
                        case "broadcast" : broadcastMessage(message); //method call if the message is to be broadcasted
                                            break;
                            case "unicast" : unicastMessage(message);//method call if the message is to be unicasted
                                            break;
                            case "except" : exceptMessage(message); //method call if the message is to be broadcasted except for one user
                                            break;

                            case "broadcastfile" : ReceiveFile(message);//method call if the file is to be broadcasted
                                                break;
                            case "unicastfile": unicastFile(message); //method call if the file is to be unicasted
                                                break;
                            case "exceptfile" : exceptFile(message); //method call if the file is to be broadcasted except for one user
                                                break;
                        }


                    }
                }
                catch(ClassNotFoundException classnot){
                    System.err.println("Data received in unknown format");
                }
            }
            catch(IOException ioException)
            {
                System.out.println("Disconnect with Client " + no +"\n");
            }
            finally
            {
                //Close connections and the stream writers and readers
                try
                {
                    in.close();
                    out.close();
                    connection.close();
                    group.remove(clientname);
                }
                catch(IOException ioException){
                    System.out.println("Disconnect with Client " + no+"\n");
                }
            }
        }

        //get command method to extract the command from the message
        private String getCommand(String message)
        {
            String command = message.substring(0,message.indexOf('#'));
            return command;
        }
        //Method to broadcast the message to everyone
        private void broadcastMessage(String message)
        {
            message=message.substring(message.indexOf("#")+1); //Extract the message by removing out the command part
            try {
                //Loop through all the connection output stream writers in the hashmap and send the message using the specific writer
                for (ObjectOutputStream writer : group.values()) {
                    if (writer != out) {
                        writer.writeObject("Broadcast Message from client " + clientname + ":" + message);
                    }
                }
            }
            //Exception Handling
            catch(IOException ioe)
            {
                ioe.printStackTrace();
            }
        }

        //Method to unicast the message to everyone
        private void unicastMessage(String message)
        {
           message =message.substring(message.indexOf("#")+1);
            String username=getCommand(message).trim();
            message=message.substring(message.indexOf("#")+1);
            //If the user is active, fetch the output stream writer for the user whom the message is intended to.
            try {
                if (!group.containsKey(username)) {
                    sendMessage("User is not connected. Try someone else");
                } else {
                    group.get(username).writeObject("Private message from "+clientname+" "+message);
                    sendMessage("Message sent to "+username);
                }
            }
            //Exception Handling
            catch(IOException ioe)
            {
                ioe.printStackTrace();
            }
        }

        //Method to broadcast the message to everyone except a specific user
        private void exceptMessage(String message)
        {
            message =message.substring(message.indexOf("#")+1);
            String username=getCommand(message).trim();
            message=message.substring(message.indexOf("#")+1);
            ObjectOutputStream temp=group.get(username);
            try {
                //Loop through all the connection output stream writers in the hashmap and send the message using the specific writer
                //Also check if the user in the except list is online
                for (ObjectOutputStream writer : group.values()) {
                    if (!writer.equals(out) && !writer.equals(temp) ) {
                        writer.writeObject("Except Message from client " + clientname + ":" + message);
                    }
                }
            }
            //Exception Handling
            catch(IOException ioe)
            {
                ioe.printStackTrace();
            }
        }

        //Method to save the file locally which is to be broadcasted to everyone
        public void ReceiveFile(String message)
        {
            String[] data = message.split("#");
            //Fetch the filename
            String filename=data[1].trim();
            //Fetch the file size before saving it
            int fileSize = Integer.parseInt(data[2].trim());

            byte [] mybytearray  = new byte [fileSize];
            // Read the file from the network and save it locally on the disk
            try{
                fos = new FileOutputStream(filename);
                bos = new BufferedOutputStream(fos);
                bytesRead = in.read(mybytearray,0,mybytearray.length);
                // in.readFully(mybytearray);
                current = bytesRead;

                while(bytesRead >= 1024){
                    bytesRead = in.read(mybytearray, current, (mybytearray.length-current));
                    if(bytesRead >= 0) current += bytesRead;
                }

                bos.write(mybytearray, 0 , current);
                bos.flush();
                System.out.println("File downloaded (" + current + " bytes read)"+"\n");
                //Methdd call to broadcast the file to everyone
                broadcastFile(filename);
            }
            //Exception Handling
            catch(Exception e){
                System.out.println(e.toString()+"\n");
            }
            finally {
                try{
                    fos.close();
                    bos.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

        //Method to broadcast the file
        private void broadcastFile(String filename)
        {
            File myFile = new File(filename);// ("C:\\Study\\makefile.txt");
            byte[] mybytearray = new byte[(int) myFile.length()];
            try {
                fis = new FileInputStream(myFile);
                bis = new BufferedInputStream(fis);
                bis.read(mybytearray, 0, mybytearray.length);
            }
            catch(IOException ioe)
            {
                ioe.printStackTrace();
            }
            try {
                //Loop through all the connection output stream writers in the hashmap and send the file using the specific writer
                //First send the filename and the size. Later then send the actual file
                for (ObjectOutputStream writer : group.values()) {
                    if (writer != out) {
                        writer.writeObject("file#" +myFile.getName()+"#"+ (int) myFile.length()+"#"+clientname);
                        writer.write(mybytearray, 0, mybytearray.length);
                        writer.flush();
                    }
                }
            }
            //Exception Handling
            catch(IOException ioe)
            {
                ioe.printStackTrace();
            }

            finally {
                try{
                    fis.close();
                    bis.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

        //Method to save the file locally which is to be unicasted to a specific user
        private void unicastFile(String message)
        {
            String[] data = message.split("#");
            String username=data[1].trim();
            String filename=data[2].trim();
            int fileSize = Integer.parseInt(data[3].trim());

            byte [] mybytearray  = new byte [fileSize];
            // Read the file from the network and save it locally on the disk
            try{
                fos = new FileOutputStream(filename);
                bos = new BufferedOutputStream(fos);
                bytesRead = in.read(mybytearray,0,mybytearray.length);
                current = bytesRead;

                while(bytesRead >= 1024){
                    bytesRead = in.read(mybytearray, current, (mybytearray.length-current));
                    if(bytesRead >= 0) current += bytesRead;
                }

                bos.write(mybytearray, 0 , current);
                bos.flush();
                System.out.println("File downloaded (" + current + " bytes read)\n");

            }
            //Exception Handling
            catch(Exception e){
                System.out.println(e.toString()+"\n");
            }
            finally {
                try{
                    fos.close();
                    bos.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            sendUnicastFile(filename,username);


        }

        //Method to unicast the file
        private void sendUnicastFile(String filename, String username)
        {
            File myFile = new File(filename);// ("C:\\Study\\makefile.txt");
            byte[] mybytearray = new byte[(int) myFile.length()];
            try {
                fis = new FileInputStream(myFile);
                bis = new BufferedInputStream(fis);
                bis.read(mybytearray, 0, mybytearray.length);
            }
            //Exception Handling
            catch(IOException ioe)
            {
                ioe.printStackTrace();
            }
            //If the user is active, fetch the output stream writer for the user whom the message is intended to.
            if(!group.containsKey(username))
            {
                sendMessage("User "+username+" not connected. Try later or contact administrator");
                return;
            }
            else {
                ObjectOutputStream writer1 = group.get(username);
                //First send the filename and the size. Later then send the actual file
                try {
                    writer1.writeObject("file#" + myFile.getName() + "#" + (int) myFile.length()+"#"+clientname);
                    writer1.write(mybytearray, 0, mybytearray.length);
                    writer1.flush();
                }
                //Exception Handling
                catch (IOException ie)
                {
                    ie.printStackTrace();
                }
                finally {
                    try{
                        fis.close();
                        bis.close();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }

        //Method to save the file locally which is to be broadcasted to everyone except a specific user
        private void exceptFile(String message)
        {
            String[] data = message.split("#");
            String username=data[1].trim();
            String filename=data[2].trim();
            int fileSize = Integer.parseInt(data[3].trim());

            byte [] mybytearray  = new byte [fileSize];
            // Read the file from the network and save it locally on the disk
            try{
                fos = new FileOutputStream(filename);
                bos = new BufferedOutputStream(fos);
                bytesRead = in.read(mybytearray,0,mybytearray.length);
                current = bytesRead;

                while(bytesRead >= 1024){
                    bytesRead = in.read(mybytearray, current, (mybytearray.length-current));
                    if(bytesRead >= 0) current += bytesRead;
                }

                bos.write(mybytearray, 0 , current);
                bos.flush();
                System.out.println("File downloaded (" + current + " bytes read)\n");

            }
            //Exception Handling
            catch(Exception e){
                System.out.println(e.toString()+"\n");
            }
            finally {
                try{
                    fos.close();
                    bos.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            sendExceptFile(filename, username);
        }

        //Method to broadcast the file to everyone except a specific user
        private void sendExceptFile(String filename,String username)
        {
            File myFile = new File(filename);// ("C:\\Study\\makefile.txt");
            byte[] mybytearray = new byte[(int) myFile.length()];
            try {
                fis = new FileInputStream(myFile);
                bis = new BufferedInputStream(fis);
                bis.read(mybytearray, 0, mybytearray.length);
            }
            //Exception Handling
            catch(IOException ioe)
            {
                ioe.printStackTrace();
            }
            ObjectOutputStream excludedwriter=group.get(username);
            try {
                //Loop through all the connection output stream writers in the hashmap except the specific user in exclusion list and send the file using the specific writer
                //First send the filename and the size. Later then send the actual file
                for (ObjectOutputStream writer : group.values()) {
                    if (!writer.equals(out) && !writer.equals(excludedwriter)) {
                        writer.writeObject("file#" +myFile.getName()+"#"+ (int) myFile.length()+"#"+clientname);
                        writer.write(mybytearray, 0, mybytearray.length);
                        writer.flush();
                    }
                }
            }
            //Exception Handling
            catch(IOException ioe)
            {
                ioe.printStackTrace();
            }
            finally {
                try{
                    fis.close();
                    bis.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

        //send a message to the output stream
        public void sendMessage(String msg)
        {
            try{
                out.writeObject(msg);
                out.flush();
                System.out.println("Send message: " + msg + " to Client " + no +"\n");
            }
            //Exception Handling
            catch(IOException ioException){
                ioException.printStackTrace();
            }
        }

    }

}
