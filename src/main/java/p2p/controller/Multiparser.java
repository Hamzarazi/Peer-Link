package p2p.controller;

import java.rmi.ServerError;

public class Multiparser {
    private final byte[] requestData;
    private final String boundary;

    public Multiparser(byte[] requestData, String boundary) {
        this.requestData = requestData;
        this.boundary = boundary;
    }

    public ParseResult parse() {
        try{
            //FileName
            String stringData = new String(requestData);
            String filenameMarker = "filename=\"";

            int filenameStart = stringData.indexOf(filenameMarker);
            if (filenameStart == -1) {
                return null;
            }
            filenameStart += filenameMarker.length();
            int filenameEnd = stringData.indexOf("\"", filenameStart);
            String fileName = stringData.substring(filenameStart, filenameEnd);

            //Content-Type
            String contentTypeMarker = "Content-Type: ";
            int ctStart = stringData.indexOf(contentTypeMarker, filenameEnd);
            String contentType = "application/octet-stream"; // default
            if (ctStart != -1) {
                ctStart += contentTypeMarker.length();
                int ctEnd = stringData.indexOf("\r\n", ctStart);
                contentType = stringData.substring(ctStart, ctEnd);
            }

            // String content

            String headerEndMarker = "\r\n\r\n";
            int headerEnd = stringData.indexOf(headerEndMarker);
            if(headerEnd == -1) return null;

            int contentStart = headerEnd + headerEndMarker.length();
            String boundaryString  = "\r\n--" + boundary + "--";
            int contentEnd = stringData.indexOf(boundaryString);
            if(contentEnd == -1)
            {
                boundaryString = "\r\n--" + boundary;
                contentEnd = stringData.indexOf(boundaryString);
            }
            if(contentEnd == -1 || contentEnd <= contentStart)
                return null;

            byte[] fileContent = new byte[contentEnd - contentStart];

            System.arraycopy(requestData, contentStart, fileContent, 0, fileContent.length);

            return new ParseResult(fileName, contentType, fileContent);
        }
        catch(Exception ex)
        {
            System.err.println("Error parsing multipart-data: "+ex.getMessage());
            return null;
        }
    }

    public static class ParseResult {
        public final String filename;
        public final String contentType;
        public final byte[] fileContent;

        public ParseResult(String filename, String contentType, byte[] fileContent) {
            this.filename = filename;
            this.contentType = contentType;
            this.fileContent = fileContent;
        }
    }
}
