/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package proxyserver;

import java.io.*;
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
    private final CommandDaoPiratteImpl cmd;
    
    //TODO find a way for the KGC to send PK and MK to Proxy Server without it getting messy. 
    
    public ProxySSLServer(int port, String keystore, char[] password, String pubkey, String masterkey) {
        keystore = System.getProperty("user.dir") + "/SSLkeys/server.jks";
        password = "password".toCharArray();
        File pk = new File(pubkey);
        File mk = new File(masterkey);
        
        this.cmd = new CommandDaoPiratteImpl();
        this.PORT = port;
        this.KEYSTORE = keystore;
        this.PWD = password;
        this.PUB_KEY = pk;
        this.MASTER_KEY = mk;      
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
                
                //Proxy Server receives initial message
                String message = streamIn.readUTF();
                System.out.println("Message received: " + message + "Preparing to re-encrypt...");
                
                if(message.equals("re-encrypt")){
                    //Proxy Server receives username
                    String username = streamIn.readUTF();
                    System.out.println("Preparing to send files to " + username + "...");

                    //Proxy Server receives filename
                    String filename = streamIn.readUTF();
                    System.out.println("Preparing to send " + filename + "...");

                    //Proxy Server receives filesize
                    String line = streamIn.readUTF();
                    int filesize = Integer.parseInt(line);
                    System.out.println("Preparing to send " + filesize + " bytes of data...");

                    //Proxy Server receives file
                    this.getFile(clientSocket, filename, filesize);

                    //Proxy Server initially creates proxy key with no revoked users
                    String [] init = {};
                    cmd.revoke(filename + "_proxykey", PUB_KEY.getAbsolutePath(), MASTER_KEY.getAbsolutePath(), init);
                    cmd.convert(username + "lambda_k", PUB_KEY.getAbsolutePath(), filename, filename + "_proxykey", username + ".id");

                    File proxyfile = new File (filename + ".proxy");

                    //Proxy Server sends proxy file size
                    String proxyfilesize = "" + (int)proxyfile.length();
                    streamOut.writeUTF(proxyfilesize);
                    streamOut.flush();

                    //Proxy Server sends proxy file name
                    streamOut.writeUTF(proxyfile.getName());
                    streamOut.flush();

                    this.sendFile(clientSocket, proxyfile);
                    System.out.println(proxyfile.getName() + " successfully sent! "
                            + "\n Preparing to shred files...");
                    cmd.remove(proxyfile.getAbsolutePath());
                    //cmd.remove(filename + "_proxykey");
                } 
                else if(message.equals("lambda-k")){
                    //Proxy Server receives username
                    String username = streamIn.readUTF();
                    System.out.println("Preparing to send lambda_k to " + username + "...");

                    //Proxy Server receives filename
                    String filename = streamIn.readUTF();
                    System.out.println("Preparing to send " + filename + "...");
                    
                    File lambdak  = new File (username + "lambda_k");
                    
                    //Proxy Server sends lambda file size
                    String lambdafilesize = "" + (int)lambdak.length();
                    streamOut.writeUTF(lambdafilesize);
                    streamOut.flush();
                    
                    //Proxy Server sends lambda_k name
                    streamOut.writeUTF(lambdak.getName());
                    streamOut.flush();

                    this.sendFile(clientSocket, lambdak);
                    cmd.remove(lambdak.getAbsolutePath());
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