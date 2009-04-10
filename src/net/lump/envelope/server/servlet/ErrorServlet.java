package us.lump.envelope.server.servlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Handle errors in a different way.
 *
 * @author troy
 * @version $Id: ErrorServlet.java,v 1.2 2009/04/10 22:49:28 troy Exp $
 */
public class ErrorServlet extends HttpServlet {
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    int error = Integer.parseInt(request.getParameter("id"));
    response.setStatus(error);
    response.setHeader("Content-Type", "text/html");
    PrintWriter out = response.getWriter();
    out.append("<html><head><title>Error</title><style><!-- body { font-family: sans-serif; } --></style></head><body>");
    out.flush();

    String name = "";

    switch (error) {
      case 400:
        name = "Bad Request";
        break;
      case 401:
        name = "Unauthorized";
        break;
      case 402:
        name = "Payment Required";
        break;
      case 403:
        name = "Forbidden";
        break;
      case 404:
        name = "Not Found";
        break;
      case 405:
        name = "Method not allowed";
        break;
      case 406:
        name = "Not Acceptable";
        break;
      case 407:
        name = "Proxy Authentication Required";
        break;
      case 408:
        name = "Request Timeout";
        break;
      case 409:
        name = "Conflict";
        break;
      case 410:
        name = "Gone";
        break;
      case 411:
        name = "Length Required";
        break;
      case 412:
        name = "Precondition Failed";
        break;
      case 413:
        name = "Request Entity Too Large";
        break;
      case 414:
        name = "Request URI Too Long";
        break;
      case 415:
        name = "Unsupported Media Type";
        break;
      case 416:
        name = "Requested Range Not Satisfiable";
        break;
      case 417:
        name = "Expectation Failed";
        break;
      case 500:
        name = "Internal Server Error";
        break;
      case 501:
        name = "Not Implemented";
        break;
      case 502:
        name = "Bad Gateway";
        break;
      case 503:
        name = "Service Unavailable";
        break;
      case 504:
        name = "Gateway Timeout";
        break;
      case 505:
        name = "HTTP Version Not Supported";
        break;
    }

    out.append("<h1>Error ").append(String.valueOf(error)).append(" ").append(name).append("</h1>");
    out.append("</body></html>");
    // for IE, stupid friendly HTTP errors.
    for (int i = 0; i < 32; i++) out.append("                ");
    out.flush();
  }
}
