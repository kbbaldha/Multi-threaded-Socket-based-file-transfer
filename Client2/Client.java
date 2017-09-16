/**
 * Created by chiranth on 11/12/2016.
 */
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

public class Client {
    Socket requestSocket;           //socket connect to the server
    ObjectOutputStream out;         //stream write to the socket
    ObjectInputStream in;          //stream read from the socket
    String message;                //message send to the server
    static String submitname="";
    static boolean displayMenu=false; //boolean value to determine if the menu should be displayed

    public void Client() {}

    void run()
    {
        try
        {
            //create a socket to connect to the server
            requestSocket = new Socket("localhost", 8000);
            System.out.println("Connected to localhost in port 8000\n");
            in = new ObjectInputStream(requestSocket.getInputStream());
            out = new ObjectOutputStream(requestSocket.getOutputStream());
            out.flush();
            //initialize inputStream and outputStream
            //Create two seperate threads for reading from the network connection and writing to the connection network
            new messageReader(in).start();
            new messageSender(out).start();
        }
        catch (ConnectException e) {
            System.err.println("Connection refused. You need to initiate a server first.");
        }

        catch(UnknownHostException unknownHost){
            System.err.println("You are trying to connect to an unknown host!");
        }
        catch(IOException ioException){
            ioException.printStackTrace();
        }
        finally{
            //Close connections

        }
    }


    //main method
    public static void main(String args[])
    {
        Client client = new Client();
        client.run();
    }

    //Thread Class to read the data sent by the server
    private static class messageReader extends Thread
    {
        ObjectInputStream in; //stream read from the socket
        String MESSAGE;     //Message read from the connection
        FileOutputStream fos;   // output stream to write the files locally which is received from server
        BufferedOutputStream bos;
        int bytesRead,current=0;

        //Initialize the connection stream reader
        public messageReader(ObjectInputStream in)
        {

            this.in=in;

        }
        public void run()
        {
            //Keep listening on the network for messages and display them to the user
            try
            {

                while(true)
                {
                   try
                   {
                       MESSAGE = (String) in.readObject();
                   }
                   catch(EOFException ex)
                   {
                        MESSAGE=null;
                   }
                    if(MESSAGE!=null)
                    {
                            //if the message recieved contains a file call method to save the file

                        if(MESSAGE.toLowerCase().contains("file"))
                        {
                                ReceiveFile(MESSAGE);

                        }

                        //If it is a normal text from the user, straightaway display it
                        else
                        {
                            //if we are still trying to set a username don't display the command options
                            if(MESSAGE.contains("successfully registered"))
                            {
                                    displayMenu=true;
                            }
                            submitname=MESSAGE;
                            System.out.println("Receive message: " + MESSAGE+"\n");
                        }
                    }

                }

            }
            //Exception Handling
            catch(IOException ioex)
            {
                System.err.println(ioex.toString());
            }
            catch ( ClassNotFoundException e )
            {
                System.err.println("Class not found");
            }
            //close the connections
            finally
            {
               try{
                    in.close();

                }
               catch(IOException ioException){
                    ioException.printStackTrace();
                }

            }
        }
        //Method to receive the file sent over the connection and save it
        public void ReceiveFile(String message)
        {
            String[] data = message.split("#");
            String filename=data[1].trim();//Fetch the filename
            int fileSize = Integer.parseInt(data[2].trim());//Fetch the file size from the message
            String sender=data[3].trim();//Fetch the sender information

            byte [] mybytearray  = new byte [fileSize];
            // InputStream is = sock.getInputStream();
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
                System.out.println("File downloaded from "+sender+" (" + current + " bytes read)\n");

            }
            //exception handling
            catch(Exception e){
                System.out.println(e.toString()+"\n");
            }
            //close the stream writers
            finally
            {
                try
                {
                    fos.close();
                    bos.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

    }

    //Thread class to send the message and data to the server.
    private static class messageSender extends Thread
    {

        private ObjectOutputStream out; //Stream writer
        String message;
        String command;
        FileInputStream fis; //File input stream
        BufferedInputStream bis;

        //Initialize the connection stream writer
        public messageSender(ObjectOutputStream out)
        {
            this.out=out;
        }

        public void run()
        {
            //attach the system reader to read the input from keyboard
            try
            {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
                //Set the username before proceeding further and we get an acknowledgement from the server that the username is available
                while(true)
                {
                    if (submitname.contains("Submit Name") || submitname.contains("Try Different Name"))
                    {

                        message = bufferedReader.readLine();
                        //Send the sentence to the server
                        sendMessage(message);
                        try
                        {
                            currentThread().sleep(500);
                        }
                        catch(InterruptedException ie)
                        {
                            ie.printStackTrace();
                        }
                    }
                    else
                    {
                        break;
                    }
                }
                //Keep on listening to the input command from the user and data to be sent to the server
                while(true)
                {
                    System.out.println("Hello, please input the command");
                    //Specify the command formats to the user
                    if(displayMenu)
                    {
                        System.out.print("For details on the format of the command see below: \n");
                        System.out.println("To broadcast a message or a file below is the syntax\n");
                        System.out.println("1)Message: broadcast# <message>\n");
                        System.out.println("2)File: broadcastfile# <filename>\n");
                        System.out.println("To unicast or send a private message or a file below is the syntax\n");
                        System.out.println("3)Message: unicast# <username># <message>\n");
                        System.out.println("4)File: unicastfile# <username># <filename>\n");
                        System.out.println("To broadcast or send a message or a file except a specific user below is the syntax\n");
                        System.out.println("5)Message: except# <username># <message>\n");
                        System.out.println("6)File: exceptfile# <username># <filename>\n");
                        displayMenu=false;
                    }
                        //read a sentence from the standard input
                    message = bufferedReader.readLine();
                    command=getCommand(message);
                    //Invoke appropriate method calls based on the command
                    switch(command.toLowerCase())
                    {
                        case "broadcastfile":sendFile(message); //Invoke the method to broadcast the file
                                break;
                        case "unicastfile" :uniorexceptfile(message); //Invoke the method to unicast the file
                                break;
                        case "exceptfile" : uniorexceptfile(message); //Invoke the method to broadcast the file except a specific user
                                break;
                        default:    sendMessage(message); // for everything else directly send the message by invoking sendmessage() method
                                break;
                    }
                }
            }
            //exception handling
            catch(IOException ioException)
            {
                ioException.printStackTrace();
            }
            //close the connection stream writer
            finally
            {
                try
                {
                    out.close();

                }
                catch(IOException ioException)
                {
                    ioException.printStackTrace();
                }
            }
        }
        //Method to extract the command from the input message read
        private String getCommand(String message){

            String command = message.substring(0,message.indexOf('#'));

            return command;
        }

        //Method to upload a file to be broadcasted among everyone
        private void sendFile(String message)
        {
            String fileName = message.substring(message.indexOf('#') + 1).trim(); //Name of the file to be sent
            File myFile = new File(fileName);//Create a file handle for the file
            byte[] mybytearray = new byte[(int) myFile.length()];
            //Initially send the file information like file name and file size before uploading the file to the network.
            sendMessage("broadcastfile#" +myFile.getName()+"#"+ (int) myFile.length());
            //Write the file to the network
            try
            {
                fis = new FileInputStream(myFile);
                bis = new BufferedInputStream(fis);
                bis.read(mybytearray, 0, mybytearray.length);
                System.out.println("Sending  (" + mybytearray.length + " bytes)\n");
                out.write(mybytearray, 0, mybytearray.length);
                out.flush();
                System.out.println("Done.\n");
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            finally
            {
                try
                {
                    fis.close();
                    bis.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

        //Method to upload a file to be unicasted or broadcasted among everyone except a specific user
        private void uniorexceptfile(String message)
        {
            String command = getCommand(message).trim(); //Extract the command
            message =message.substring(message.indexOf("#")+1);
            String username=getCommand(message).trim(); //Extract the username
            String fileName=message.substring(message.indexOf("#")+1).trim(); //Name of the file to be sent
            File myFile = new File(fileName);//Create a file handle for the file
            byte[] mybytearray = new byte[(int) myFile.length()];
            //Initially send the file information like file name and file size before uploading the file to the network.
            //Also send the username to be unicasted or excluded from the list
            sendMessage(command+"#"+username+"#" +myFile.getName()+"#"+ (int) myFile.length());
            //Write the file to the network
            try
            {
                fis = new FileInputStream(myFile);
                bis = new BufferedInputStream(fis);
                bis.read(mybytearray, 0, mybytearray.length);
                System.out.println("Sending  (" + mybytearray.length + " bytes)\n");
                out.write(mybytearray, 0, mybytearray.length);
                out.flush();
                System.out.println("Done.\n");
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            finally
            {
                try
                {
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
        void sendMessage(String msg)
        {
            try{
                //stream write the message
                out.writeObject(msg);
                out.flush();
            }
            //Exception Handling
            catch(IOException ioException)
            {
                ioException.printStackTrace();
            }
        }
    }

    }
