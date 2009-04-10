package us.lump.envelope.server.servlet;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import us.lump.envelope.server.log.Log4j;
import us.lump.envelope.server.servlet.beans.FileServer;
import us.lump.envelope.server.servlet.beans.ServerPrefs;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * This servlet handles feeding the jnlp file, jars, and classes to the client.
 *
 * @author troy
 * @version $Id: DefaultServlet.java,v 1.2 2009/04/10 22:49:28 troy Exp $
 */
public class DefaultServlet extends HttpServlet {

  private static final Logger logger = Logger.getLogger(DefaultServlet.class);

  @Override public void log(String s) {
    logger.info(s);
  }

  @Override public void log(String s, Throwable throwable) {
    logger.info(s, throwable);
  }

  @Override public void init() {
    try {
      PropertyConfigurator.configure(ServerPrefs.getInstance().getProps(Log4j.class));
    } catch (IOException ignore) {}
  }

  @Override protected void doPost(HttpServletRequest rq, HttpServletResponse rp)
      throws ServletException, IOException {
    doGet(rq, rp);
  }

  @Override protected void doHead(HttpServletRequest rq, HttpServletResponse rp)
      throws ServletException, IOException {
    doGet(rq, rp);
  }

  @Override protected void doGet(HttpServletRequest rq, HttpServletResponse rp)
      throws ServletException, IOException {
    new FileServer(rq, rp);
  }
}
