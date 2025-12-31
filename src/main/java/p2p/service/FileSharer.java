package p2p.service;

import p2p.utils.utilsUpload;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class FileSharer {

    private HashMap<Integer, String> availableFiles;

    public FileSharer() {
        availableFiles = new HashMap<>();
    }

    public int offerFile(String filePath) {
        int port;
        while (true) {
            port = utilsUpload.generatePort();
            if (!availableFiles.containsKey(port)) {
                availableFiles.put(port, filePath);
                return port;
            }
        }
    }

    public void startFileServer(int port) {

        String filePath = availableFiles.get(port);
        if (filePath == null) {
            System.err.println("No file is available on the given port: " + port);
            return;
        }
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("File with filename: " + new File(filePath).getName() + " is available on port: " + port);
            Socket clientSocket = serverSocket.accept();
            System.out.println("Connection established and client connected at : " + clientSocket.getInetAddress());

            new Thread(new SendFileHandler(clientSocket, filePath)).start();
        } catch (IOException ex) {
            System.out.println("Error occurred while serving file on port: " + port + " " + ex.getMessage());
        }
    }

    public static class SendFileHandler implements Runnable {
        private final Socket clientSocket;
        private final String filePath;

        public SendFileHandler(Socket clientSocket, String filePath) {
            this.clientSocket = clientSocket;
            this.filePath = filePath;
        }

        @Override
        public void run() {
            try (FileInputStream fis = new FileInputStream(filePath);
                 OutputStream oss = clientSocket.getOutputStream()) {

                //Sending file name as header
                String fileName = new File(filePath).getName();
                String header = "Filename: " + fileName + "\n";
                oss.write(header.getBytes());

                //Sending file to clientSocket
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    oss.write(buffer, 0, bytesRead);
                }
                System.out.println("File: " + fileName + " sent to port: " + clientSocket.getInetAddress());
            } catch (IOException e) {
                System.err.println("Error sending file to client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }

        }
    }
}