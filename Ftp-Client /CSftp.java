
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.System;
import java.io.IOException;
import java.net.Socket;
import java.io.*;
import java.net.*;
import java.util.Scanner;

//
// This is an implementation of a simplified version of a command 
// line ftp client. The program always takes two arguments
//


public class CSftp
{
    static final int MAX_LEN = 255;

    private static Socket socket;
    private static Socket dataSocket;

    private static int port;
    private static final int DEFAULT_PORT = 21;
    private static BufferedWriter out;
    private static BufferedReader in;
    private static String hostName;

    public static void main(String [] args) {

        // Get command line arguments and connect to FTP
        // If the arguments are invalid or there aren't enough of them
        // then exit.

        if (args.length == 1){
            hostName = args[0];
            port = DEFAULT_PORT;
            System.out.println("Connected to " + hostName + ".");
        }
        else if (args.length == 2) {
            hostName = args[0];
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.print("Usage: cmd ServerAddress ServerPort\n");
                return;
            }
            System.out.println("Connected to " + hostName + ".");
        }
        else {
            System.out.print("Usage: cmd ServerAddress ServerPort\n");
            return;
        }

        try {

            socket = new Socket(); // create socket and set up input and output stream
            socket.connect(new InetSocketAddress(hostName, port), 20000); // connect socket, set timeout to 20 seconds
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            //Establishing the connection

            String fromServer = readFromServer();

            if(fromServer.startsWith("421")){
                System.out.println("Server: 421 " + "Service not available, closing control connection.");
                System.exit(1);
            }

            else if (fromServer.startsWith("220")) {
                    System.out.println("Service ready for new user.");
            }
            else if (fromServer.startsWith("120")){
                    System.out.println("Service ready soon.");
                }
            try {
                for (int len =1 ; len > 0;) {

                    System.out.print("csftp> ");
                    byte cmdString[] = new byte[MAX_LEN];
                    len = System.in.read(cmdString);
                    // Change the byte into String.
                    String str = new String(cmdString, "UTF-8");
                    // Split string into arguments
                    String[] input = str.trim().split("\\s+");
                    String cmd = input[0];

                    if (cmd.equals("") || str.startsWith("#")) { // ignore empty lines or lines starting with #
                        continue;
                    } else {
                        switch (cmd) {
                            case "user":
                                if (input.length != 2) {
                                    System.out.println("0x002 Incorrect number of arguments.");
                                } else {
                                    user(input[1]); // input[1] = username
                                }
                                break;
                            case "pw":
                                if (input.length != 2) {
                                    System.out.println("0x002 Incorrect number of arguments.");
                                } else {
                                    pasw(input[1]); // input[1] = password
                                }
                                break;
                            case "quit":
                                if (socket != null) {
                                    if (input.length != 1) { // only quit if there are no parameters
                                        System.out.println("0x002 Incorrect number of arguments.");
                                        break;
                                    }
                                    else {
                                        writeToServer("QUIT");
                                        readFromServer();
                                        socket.close();
                                        System.exit(1);
                                    }
                                }
                            case "get":
                                if (input.length != 2) {
                                    System.out.println("0x002 Incorrect number of arguments.");
                                } else {
                                    get(input[1]); // input[1] = file
                                }
                                break;
                            case "features":
                                if (input.length != 1) {
                                    System.out.println("0x002 Incorrect number of arguments.");
                                } else {
                                    features();
                                }
                                break;
                            case "cd":
                                if (input.length != 2) {
                                    System.out.println("0x002 Incorrect number of arguments.");
                                } else {
                                    cd(input[1]); // input[1] = dir
                                }
                                break;
                            case "dir":
                                if (input.length != 1) {
                                    System.out.println("0x002 Incorrect number of arguments.");
                                } else {
                                    dir();
                                }
                                break;
                            default:
                                System.out.println("0x001 Invalid command.");
                        }
                    }
                }

            } catch (IOException e) {
                System.out.println("0xFFFE Input error while reading commands, terminating.");
                System.exit(1);
            }
        }
        catch (UnknownHostException e) {
            System.out.println("0xFFFC Control connection to "+ hostName + " on port " + port + " failed to open.");
            System.exit(1);
        }
        catch (SocketTimeoutException e) {
            System.out.println("0xFFFC Control connection to "+ hostName + " on port " + port + " failed to open.");
            System.exit(1);
        }
        catch (IOException e) {
            System.out.println("0xFFFD Control connection I/O error, closing control connection.");
            System.exit(1);
        }
        catch(ArrayIndexOutOfBoundsException e){
            System.out.println(e);
            System.exit(1);
        }
        catch (UnknownError e){
            System.out.print("0xFFFF Processiing error. " + e);
            System.exit(1);
        }
    }

    private static void user(String usern) throws IOException {

        try {
            writeToServer("USER " + usern);
            String response = readFromServer();
            if (response.startsWith("421")) { // service not available
                System.exit(1);
            }
            if (response.startsWith("331")) { // user name okay, need password
                pw(); // Enter password in pw
            }
        } catch (IOException e) {
            System.out.println("0xFFFE Input error while reading commands, terminating.");
            System.exit(1);
        }
    }

    private static void pw(){
        System.out.print("Enter password: ");
        Scanner getPass = new Scanner (System.in);
        String pass = getPass.nextLine();
        try {
            writeToServer("PASS " + pass);
            String response = readFromServer();
            if (response.startsWith("421")) { // service not available
                System.exit(1);
            }
        } catch (IOException e) {
            System.out.println("0xFFFD Control connection I/O error, closing control connection.");
            System.exit(1);
        }
    }

    // password if using the pw command with an argument
    private static void pasw(String pass) {
        try {
            writeToServer("PASS " + pass);
            String response = readFromServer();
            if (response.startsWith("421")) { // service not available
                System.exit(1);
            }
        } catch (IOException e) {
            System.out.println("0xFFFD Control connection I/O error, closing control connection.");
            System.exit(1);
        }
    }

    private static void get(String file) throws IOException {
        writeToServer("TYPE I"); // switch to binary mode
        String response = readFromServer();
        if (response.startsWith("421")) { // service not available
            System.exit(1);
        }
        if (response.startsWith("200")) { // continue if command okay
            writeToServer("PASV"); // passive mode
        }
        response = readFromServer();
        if (response.startsWith("227")) {
            String[] parsedResponse = parsePassiveResponse(response); // get host and port from response
            String host = getHost(parsedResponse);
            int port = getPort(parsedResponse);
            try {
                dataSocket = new Socket();
                dataSocket.connect(new InetSocketAddress(host, port), 10000); // connect data socket with timeout 10 seconds
            } catch (SocketTimeoutException e) {
                System.out.println("0x3A2 Data transfer connection to " + host + " on port " + port + " failed to open.");
                dataSocket.close();
                return;
            }
        }
        try {
            writeToServer("RETR " + file);
            response = readFromServer();
            if (response.startsWith("150") || response.startsWith("125")) { // data connection open
                InputStream input = dataSocket.getInputStream();
                FileOutputStream output = new FileOutputStream(file);
                byte[] buffer = new byte[2048];
                while (input.read(buffer) != -1) {
                    output.write(buffer); // write to file
                }
                readFromServer();
                input.close();
                output.close();
                dataSocket.close();
            }
        }
        catch (FileNotFoundException e) {
            readFromServer();
            System.out.println("0x38E Access to local file " + file + " denied.");
            dataSocket.close();
        }
        catch (IOException e) {
            readFromServer();
            System.out.println("0x3A7 Data transfer connection I/O error, closing data connection.");
            dataSocket.close();
        }
    }

    private static void dir() throws IOException {
        writeToServer("PASV"); // passive mode
        String response = readFromServer();
        if (response.startsWith("421")) { // service not available
            System.exit(1);
        }
        if (response.startsWith("227")) {
            String[] parsedResponse = parsePassiveResponse(response); // get host and port from response
            String host = getHost(parsedResponse);
            int port = getPort(parsedResponse);
            try {
                dataSocket = new Socket();
                dataSocket.connect(new InetSocketAddress(host, port), 10000); // connect data socket with timeout 10 seconds
            } catch (SocketTimeoutException e) {
                System.out.println("0x3A2 Data transfer connection to " + host + " on port " + port + " failed to open.");
                dataSocket.close();
                return;
            }
        }
        try {
            writeToServer("LIST");
            response = readFromServer();
            if (response.startsWith("150") || response.startsWith("125")) { // data connection open
                String wd;
                BufferedReader br;
                br = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
                while ((wd = br.readLine()) != null) {
                    System.out.println(wd); // print contents of directory
                }
                readFromServer();
                br.close();
                dataSocket.close();
            }
        } catch (IOException e) {
            readFromServer();
            System.out.println("0x3A7 Data transfer connection I/O error, closing data connection.");
            dataSocket.close();
        }
    }

    private static void features() throws IOException {
        try {
            writeToServer("FEAT");
            String response = readFromServer();
            if (response.startsWith("421")) { // service not available
                System.exit(1);
            }
        } catch (IOException e) {
            System.out.println("0xFFFD Control connection I/O error, closing control connection.");
            System.exit(1);
        }
    }

    private static void cd(String dir) throws IOException {
        try {
            writeToServer("CWD " + dir);
            String response = readFromServer();
            if (response.startsWith("421")) { // service not available
                System.exit(1);
            }
        } catch (IOException e) {
            System.out.println("0xFFFD Control connection I/O error, closing control connection.");
            socket.close();
            System.exit(1);
        }
    }

    private static String readFromServer() throws IOException {
        String response = null;
        try {
            response = in.readLine();
            if (response.substring(0, 4).matches("\\d{3}\\s")) {
                System.out.println("<-- " + response);
            } else {
                while (!response.substring(0, 4).matches("\\d{3}\\s")) { // print multi-line responses
                    response = in.readLine();
                    System.out.println("<-- " + response);
                }
            }
        } catch (IOException e) {
            System.out.println("0xFFFD Control connection I/O error, closing control connection.");
            socket.close();
            System.exit(1);
        }

        return response;
    }

    private static void writeToServer(String line) throws IOException  {
        System.out.println("--> " + line);
        try {
            out.write(line + "\r\n");
            out.flush();
        } catch (IOException e) {
            System.out.println("0xFFFD Control connection I/O error, closing control connection.");
            socket.close();
            System.exit(1);
        }
    }

    private static String[] parsePassiveResponse(String response) {
        String passive = response.substring(response.indexOf("(") + 1, response.indexOf(")"));
        return passive.split(",");
    }

    private static String getHost(String[] response) {
        String host = response[0] + "." + response[1] + "." + response[2] + "." + response[3];
        return host;
    }

    private static int getPort(String[] response) {
        int port = Integer.parseInt(response[4]) * 256 + Integer.parseInt(response[5]);
        return port;
    }
}










