package us.lump.envelope.server.http;

/**
 * RequestHandler interface
 */
public interface RequestHandler
{

    /**
     * Handles the incoming request
     *
     * @param socket    The socket communication back to the client
     */
    public void handleRequest( java.net.Socket socket );
}