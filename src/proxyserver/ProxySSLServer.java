/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package proxyserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import proxyserver.dao.CommandDaoPiratteImpl;
import proxyserver.dao.CommandFailedException;

/**
 *
 * @author issa
 */
public class ProxySSLServer implements Runnable{
    
    private final int PORT;
    private final String KEYSTORE;
    private final char[] PWD;
    private final File PUB_KEY;
    private final File MASTER_KEY;
    
    private CommandDaoPiratteImpl cmd = new CommandDaoPiratteImpl();
    
    //TODO find a way for the KGC to send PK and MK to Proxy Server without it getting messy. 
    
    public ProxySSLServer(int port, String keystore, char[] pwd) {
        this.PORT = port;
        this.KEYSTORE = System.getProperty("user.dir") + "/SSLkeys/server.jks";
        this.PWD = "password".toCharArray();
        this.PUB_KEY = new File("pk");
        this.MASTER_KEY = new File("mk");      
    }
    
    @Override
    public void run(){
        char ksPass[] = PWD;
        char ctPass[] = PWD;
        
        try {
            //Load Key Store File
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream(KEYSTORE), ksPass);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, ctPass);

            //Initialize SSL Connection
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(kmf.getKeyManagers(), null, null);
            SSLServerSocketFactory ssf = sc.getServerSocketFactory();
            SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(PORT);
            System.out.println("Proxy Server running on port " + PORT + "...");

            while(!Thread.currentThread().isInterrupted()){
                //Connect to Client
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                System.out.println("Accepted connection : " + clientSocket); 
                 
                DataOutputStream streamOut = new DataOutputStream(clientSocket.getOutputStream());
                DataInputStream streamIn = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
                
                //Proxy Server receives code
                String code = streamIn.readUTF();
                System.out.println(code);
                
                if(code.equals("re-encrypt")){
                    //Proxy Server receives username
                    String username = streamIn.readUTF();
                    System.out.println(username);

                    //Proxy Server receives filename
                    String filename = streamIn.readUTF();
                    System.out.println(filename);

                    //Proxy Server receives filesize
                    String line = streamIn.readUTF();
                    int filesize = Integer.parseInt(line);
                    System.out.println("" + filesize);

                    //Proxy Server receives file
                    this.getFile(clientSocket, filename, filesize);

                    //Proxy Server initially creates proxy key with no revoked users
                    String [] init = {};
                    cmd.revoke(filename + "_proxy_key", PUB_KEY.getAbsolutePath(), MASTER_KEY.getAbsolutePath(), init);
                    cmd.convert(username + "lambda_k", PUB_KEY.getAbsolutePath(), filename, filename + "_proxy_key", username + ".id");

                    File proxy_file = new File (filename + ".proxy");

                    //Proxy Server sends proxy file size
                    String proxy_filesize = "" + (int)proxy_file.length();
                    streamOut.writeUTF(proxy_filesize);
                    streamOut.flush();

                    //Proxy Server sends proxy file name
                    streamOut.writeUTF(proxy_file.getName());
                    streamOut.flush();

                    this.sendFile(clientSocket, proxy_file);
                    System.out.println(proxy_file.getName() + " successfully sent");
                } 
                else if(code.equals("lambda-k")){
                    //Proxy Server receives username
                    String username = streamIn.readUTF();
                    System.out.println(username);

                    //Proxy Server receives filename
                    String filename = streamIn.readUTF();
                    System.out.println(filename);
                    
                    File lambda_k  = new File (username + "lambda_k");
                    
                    //Proxy Server sends lambda file size
                    String lambda_filesize = "" + (int)lambda_k.length();
                    streamOut.writeUTF(lambda_filesize);
                    streamOut.flush();
                    
                    //Proxy Server sends lambda_k name
                    streamOut.writeUTF(lambda_k.getName());
                    streamOut.flush();

                    this.sendFile(clientSocket, lambda_k);
                }
            }
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException | KeyManagementException | NumberFormatException | CommandFailedException e) {
        }
    }
        
    private void sendFile(SSLSocket socket, File myFile) throws FileNotFoundException, IOException {     
        FileInputStream fis = new FileInputStream(myFile);
        BufferedInputStream bis = new BufferedInputStream(fis);
        BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
        byte[] bytes = new byte[(int)myFile.length()];

        int count;
        while ((count = bis.read(bytes)) > 0) {
            out.write(bytes, 0, count);
        }

        out.flush();
        //out.close();
        //fis.close();
        //bis.close();
    }
   
    private void getFile(SSLSocket socket, String get_filename, int fileSize) throws IOException{
        BufferedInputStream get = new BufferedInputStream(socket.getInputStream());
        PrintWriter put = new PrintWriter(socket.getOutputStream(),true);
        
        // receive file
        byte [] mybytearray  = new byte [fileSize];
        InputStream is = socket.getInputStream();
        FileOutputStream fos = new FileOutputStream(get_filename);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        int bytesRead = is.read(mybytearray,0,mybytearray.length);
        int current = bytesRead;

        do {
           bytesRead = is.read(mybytearray, current, (mybytearray.length-current));
           if(bytesRead >= 0) current += bytesRead;
        } while(current < fileSize);

        bos.write(mybytearray, 0 , current);
        bos.flush();
        System.out.println("File " + get_filename
            + " downloaded (" + current + " bytes read)");
    }         
}