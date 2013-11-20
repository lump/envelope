package net.lump.envelope.server.servlet.jnlp;

import net.lump.envelope.client.Main;
import net.lump.lib.util.Revision;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * JNLP.
 */
public class Jnlp {
  String jnlp;

  public Jnlp(String hostname, int port, String context) throws IOException {
    byte[] bjnlp = slurpInputSteam(this.getClass().getResourceAsStream("jnlp.xml"));

    jnlp = new String(bjnlp);
    jnlp = jnlp.replaceAll("\\{host\\}", hostname);
    jnlp = jnlp.replaceAll("\\{port\\}", String.valueOf(port));
    jnlp = jnlp.replaceAll("\\{title\\}", "Envelope");
    jnlp = jnlp.replaceAll("\\{revision\\}", Revision.nameOrState());
    jnlp = jnlp.replaceAll("\\{vendor\\}", "Lump Software");
    jnlp = jnlp.replaceAll("\\{description\\}", "An Envelope Budget");
    jnlp = jnlp.replaceAll("\\{icon\\}", "lib/envelope_32.png");
    jnlp = jnlp.replaceAll("\\{main-class\\}", Main.class.getName());
    jnlp = jnlp.replaceAll("\\{context\\}", context);

//          for (String file : new String[]{"slim-client.jar.pack.gz",
//                                          "slim-client.jar",
//                                          "client.jar.pack.gz",
//                                          "client.jar"}) {
//            if (ClassLoader.getSystemResource("lib/"+file) != null) {
//              jnlp =
//                  jnlp.replaceAll("\\{jars\\}",
//                                  "<jar href=\"lib/"+file+"\">\n");
//              break;
//            }
//          }
//
    jnlp = jnlp.replaceAll("\\{jars\\}",
                           "<jar href=\"lib/client.jar\">\n");

  }

  public String toString() {
    return jnlp;
  }

  private byte[] slurpInputSteam(InputStream is) throws IOException {
    DataInputStream dis = new DataInputStream(is);
    int bytesRead;
    byte[] content = new byte[0];
    byte[] buffer = new byte[1024];
    while ((bytesRead = dis.read(buffer, 0, buffer.length)) != -1) {
      byte[] newContent = new byte[content.length + bytesRead];
      System.arraycopy(content, 0, newContent, 0, content.length);
      System.arraycopy(buffer, 0, newContent, content.length, bytesRead);
      content = newContent;
    }
    return content;
  }

}
