package proxyserver.dao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
/*
 * @author Isabelle Tingzon
 * @edited Angelu Kaye Tiu
 */
public class CommandDaoPiratteImpl implements CommandDao{
    
    // Executes bash commands in java
    // PIRATTE Command Line Tool by Sonia Jahid, University of Illinois at Urbana-Champaign.
    // For more information, visit: http://www.soniajahid.com	
    
    private final String ABEIMPL;
    private final String ENCRYPT;
    private final String DECRYPT;
    
    
    public CommandDaoPiratteImpl() {
        this.ABEIMPL = System.getProperty("user.dir")+"/piratte/";
        this.ENCRYPT = ABEIMPL + "easier-enc";
        this.DECRYPT = ABEIMPL + "easier-dec";
    }
    
    @Override
    public void convert(String lambda_k, String pub_key, String file, String proxy_key, String id) throws CommandFailedException{
        String[] command = {ABEIMPL + "easier-convert", "-l", lambda_k, pub_key, file, proxy_key, id};
        execute(command, "easier-convert"); 
    }
    
    @Override
    public void revoke(String proxy_key, String pub_key, String master_key, String[] revoked_users) throws CommandFailedException {
        String[] command = {ABEIMPL + "easier-revoke", "-o", proxy_key, pub_key, master_key};
        String[] commandInput = new String[command.length + revoked_users.length];
        if (revoked_users.length > 0){
            System.arraycopy(command, 0, commandInput, 0, command.length);
            System.arraycopy(revoked_users, 0, commandInput, command.length, revoked_users.length); 
            execute(commandInput, "easier-revoke");
        }
        else {
            execute(command, "easier-revoke");
        }
    }
         
    
    @Override
    public void execute(String[] command, String strcom) throws CommandFailedException{
        StringBuilder output = new StringBuilder();
        ProcessBuilder pb = new ProcessBuilder(command);
        try {
            Process p = pb.start();
            BufferedReader reader = 
                        new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader error = 
                            new BufferedReader(new InputStreamReader(p.getErrorStream()));

            //Read output of commands (if status is not 0)
            String line;           
            while ((line = reader.readLine())!= null) {
                output.append(line).append("\n");
            }
            while ((line = error.readLine())!= null){
                output.append(line).append("\n");
            }

            //returns status code of command; 0 if successful
            int status_code = p.waitFor();
            System.out.println(strcom + " exited with status code " + status_code + ". " + output);

            if (status_code!=0) throw new CommandFailedException();
        } catch (IOException | InterruptedException e) {}
        
    }
}