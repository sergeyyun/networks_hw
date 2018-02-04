//Acknowledgement: used textbook materials at https://media.pearsoncmg.com/aw/aw_kurose_network_3/labs/lab1/lab1.html

import java.io.*;
import java.net.*;
import java.util.*;

public class WebServer{
    public static void main(String[] args){

        //check if there is exactly one command line argument
        if(args.length != 1){
            System.out.println("Usage: java EchoServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);

        ListenSocket(port);

    }

    private static void ListenSocket(int port){
        //initialize welcome socket
        ServerSocket welcomeSocket = null;
        try {
            welcomeSocket = new ServerSocket(port);
        } catch (IOException e) {
            System.out.println("Error: could not listen to port " + port);
            System.exit(1);
        }
            //start new thread on accepting each connection request
            while (true) {
                HttpRequest httpRequest;
                try {
                    httpRequest = new HttpRequest(welcomeSocket.accept());
                    Thread thread = new Thread(httpRequest);
                    thread.start();
                } catch (IOException e) {
                    System.out.println("Error: could not accept connection request");
                    System.exit(1);
                }
            }
    }
}

class HttpRequest implements Runnable
{
    Socket socket;
    BufferedReader inputStream;
    DataOutputStream outputStream;
    HashMap<String, String> redirects;

    public HttpRequest(Socket socket){
        this.socket = socket;
        try {
            inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            outputStream = new DataOutputStream(socket.getOutputStream());
        } catch(IOException e){
            System.out.println("Error: could not open input and output streams");
            System.exit(1);
        }
    }

    public void run(){
        System.out.println("Connection accepted on socket " + socket.toString());
        processRequest();
    }

    private void processRequest(){
        //get request line
        String requestLine = null;
        try{
            requestLine = inputStream.readLine();
        }catch (IOException e){
            System.out.println("Error: could not read the request");
            System.exit(1);
        }
        System.out.println("Http request received: " + requestLine);

        //process redirect.defs
        redirects = ProcessRedirects();

        //parse request line
        if(requestLine != null) {
            String parsedRequestLine[] = requestLine.split(" ", 3);
            String method = parsedRequestLine[0];
            String url = parsedRequestLine[1];
            String version = parsedRequestLine[2];

            //check if url is in redirects list and process redirect request if true
            boolean isRedirect = false;
            for (String key : redirects.keySet()) {
                if (key.equals(url)) {
                    url = redirects.get(key);
                    isRedirect = true;
                    try {
                        ProcessRedirectRequest(url, version);
                    } catch (IOException e) {
                        System.out.println("Error: could not process redirect request");
                        System.exit(1);
                    }
                }
            }

            //if method is GET, process GET request
            if (!isRedirect && method.equals("GET")) {
                try {
                    ProcessGetRequest(url, version);
                } catch (IOException e) {
                    System.out.println("Error: could not process GET request");
                    System.exit(1);
                }
            }

            //if method is HEAD, process HEAD request
            else if (method.equals("HEAD")) {
                System.out.println("handle HEAD request");
            } else {
                try {
                    NotAllowed(version);
                } catch (IOException e) {
                    System.out.println("Error: could not process 405 not allowed response");
                    System.exit(1);
                }
            }
        }

        //close streams and connection socket
        try {
            inputStream.close();
            outputStream.close();
            socket.close();
        } catch (IOException e){
            System.out.println("Error: could not close the streams and the socket");
            System.exit(1);
        }
    }

    private void ProcessGetRequest(String url, String version) throws IOException{

        String fileExtension = url.substring(url.lastIndexOf(".") + 1);
        String fileName = "./www" + url;

        //define content type based on file extension
        String contentType = null;
        if(fileExtension.equals("html")){contentType = "text/html";}
        else if(fileExtension.equals("txt")){contentType = "text/plain";}
        else if(fileExtension.equals("pdf")){contentType = "application/pdf";}
        else if(fileExtension.equals("png")){contentType = "image/png";}
        else if(fileExtension.equals("jpg")){contentType = "image/jpg";}

        try{
            FileInputStream fileInputStream = new FileInputStream(fileName);

            //construct response headers
            String statusLine = version + " " + "200" + " " + "OK" + "\r\n";
            String contentTypeLine = "Content-Type: " + contentType + "\r\n";
            String headers = statusLine + contentTypeLine + "\r\n";
            outputStream.writeBytes(headers);

            //send requested file contents
            byte[] buffer = new byte[1024];
            int count = 0;

            while ((count = fileInputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, count);
            }

            outputStream.flush();
        } catch (FileNotFoundException e) {
            NotFound(version);
        }
    }

    //handle HEAD (200)

    //handle not found (404)
    private void NotFound(String version) throws IOException{
        String statusLine = version + " " + "404" + " " + "Not Found" + "\r\n";
        String contentTypeLine = "Content-Type: " + "text/plain" + "\r\n";
        String headers = statusLine + contentTypeLine + "\r\n";
        String content = "404: Requested file not found\r\n";
        String response = headers + content;
        outputStream.writeBytes(response);
    }

    //handle bad request (400)
    private void BadRequest(String version) throws IOException{
        String statusLine = version + " " + "400" + " " + "Bad Request" + "\r\n";
        String contentTypeLine = "Content-Type: " + "text/plain" + "\r\n";
        String headers = statusLine + contentTypeLine + "\r\n";
        String content = "400: Bad Request\r\n";
        String response = headers + content;
        outputStream.writeBytes(response);
    }

    //handle not allowed (405)
    private void NotAllowed(String version) throws IOException{
        String statusLine = version + " " + "405" + " " + "Method Not Allowed" + "\r\n";
        String contentTypeLine = "Content-Type: " + "text/plain" + "\r\n";
        String headers = statusLine + contentTypeLine + "\r\n";
        String content = "405: Method Not Allowed\r\n";
        String response = headers + content;
        outputStream.writeBytes(response);
    }

    //process redirect.defs
    private HashMap<String, String> ProcessRedirects(){
        try {
            Scanner scanner = new Scanner(new FileReader("./www/redirect.defs"));
            HashMap<String,String> redirectMap = new HashMap<>();

            while (scanner.hasNextLine()) {
                String[] columns = scanner.nextLine().split(" ");
                redirectMap.put(columns[0],columns[1]);
            }
            return redirectMap;

        } catch (IOException e){
            System.out.println("Could not read redirect.defs");
            return null;
        }
    }

    //process redirect requests
    private void ProcessRedirectRequest(String url, String version) throws IOException{
        //construct response headers
        String statusLine = version + " " + "301" + " " + "Moved Permanently" + "\r\n";
        String locationLine = "Location: " + url + "\r\n";
        String headers = statusLine + locationLine + "\r\n";
        outputStream.writeBytes(headers);
        outputStream.close();
    }
}
