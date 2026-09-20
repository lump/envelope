package net.lump.envelope.server.servlet;

import net.lump.envelope.server.ThreadInfo;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Puts the request on the thread for the log, for the whole of its handling,
 * and takes everything off the thread again when it is done.
 *
 * <p>Mapped to every path in web.xml, so it wraps the three servlets alike:
 * a transaction begun by the readiness probe reads as
 * {@code [GET /envelope/info/ready 127.0.0.1] [no-session ready] began transaction}
 * and one begun for a client as {@code [POST /envelope/invoke 10.0.0.5] [guest
 * listTransactions] began transaction}, the second bracket being what {@link
 * net.lump.envelope.server.Controller} adds once it has the command.  The remote address is the real client's when Tomcat's RemoteIpValve
 * is in front (the image configures it), the proxy's otherwise.
 *
 * <p>Clearing in a finally here is the outer guarantee that a worker thread
 * starts each request clean, whatever a servlet did or threw; Controller still
 * clears the user itself, at the point it knows the dispatch is over.
 *
 * @author troy
 */
public class RequestContextFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (request instanceof HttpServletRequest rq) {
      ThreadInfo.setRequest(rq.getMethod() + " " + rq.getRequestURI() + " " + rq.getRemoteAddr());
    }
    try {
      chain.doFilter(request, response);
    } finally {
      ThreadInfo.clear();
    }
  }
}
