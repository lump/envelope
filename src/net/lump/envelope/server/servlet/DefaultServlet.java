package net.lump.envelope.server.servlet;

import net.lump.envelope.server.servlet.beans.FileServer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * This servlet handles feeding the jnlp file, jars, and classes to the client.
 *
 * @author troy
 * @version $Id: DefaultServlet.java,v 1.4 2009/10/02 22:06:23 troy Exp $
 */
public class DefaultServlet extends HttpServlet {

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
