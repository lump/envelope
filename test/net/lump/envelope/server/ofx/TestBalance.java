package us.lump.envelope.server.ofx;

import junit.framework.TestCase;
import net.ofx.types.OFX;
import net.ofx.types.SignonRequest;
import net.ofx.types.SignonRequestMessageSetV1;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import java.io.FileOutputStream;

//
// TestBalance
//
// Copyright 2008 SOS Staffing all rights reserved
//

/**
 * .
 *
 * @author Troy Bowman
 * @version $Revision: 1.1 $
 */
public class TestBalance extends TestCase {
  //http://java.sun.com/developer/technicalArticles/WebServices/jaxb/index.html
  @Test
  public void testLogin() throws Exception {
    OFX ofx = new OFX();

    SignonRequest srq = new SignonRequest();
    srq.setUSERID("4166559");
    srq.setUSERPASS("blah");
    SignonRequestMessageSetV1 srqm = new SignonRequestMessageSetV1();
    srqm.setSONRQ(srq);
    ofx.setSIGNONMSGSRQV1(srqm);

    JAXBContext jaxbContext = JAXBContext.newInstance("net.ofx.type");
    Marshaller marshaller = jaxbContext.createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, new Boolean(true));
    marshaller.marshal(ofx, new FileOutputStream("OFX.xml"));
  }

  protected void setUp() throws Exception {
    super.setUp();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }

}
