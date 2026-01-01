package p2p.controller;

import p2p.service.FileSharer;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

public class FileController {

    private FileSharer fileSharer;
    private HttpServer server;
    private ExecutorService executorService;
    private String uploadDir;

    public FileController(int port) throws IOException {
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.executorService = Executors.newFixedThreadPool(10);
        this.uploadDir = System.getProperty("java.io.tmpdir") + File.separator + "peerlink-uploads";

        File uploadDirFile = new File(uploadDir);
        if(!uploadDirFile.exists()){
            uploadDirFile.mkdirs();
        }

        server.createContext("upload", new UplaodHandler());
        server.createContext("download", new DownlaodHandler());
        server.createContext("/", new CORSHandler());

        server.setExecutor(executorService);
    }

    public void start(){
        server.start();
        System.out.println("API server started on port: " + server.getAddress().getPort());
    }

    public void stop()
    {
        server.stop(0);
        executorService.shutdown();
        System.out.println("API server stopped");
    }

}
