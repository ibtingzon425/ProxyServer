package proxyserver.dao;

/**
 *
 * @author angelukayetiu
 */
public interface CommandDao {

    public void convert(String lambda_k, String pub_key, String file, String proxy_key, String id) throws CommandFailedException;
    public void revoke(String proxy_key, String pub_key, String master_key, String[] revoked_users) throws CommandFailedException;
    public void execute(String[] command, String strcom) throws CommandFailedException;
}
