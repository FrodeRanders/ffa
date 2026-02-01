package se.fk.hundbidrag;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit test for simple App.
 */
public class SerialiseringsTest
    extends TestCase
{
    private static final Logger log = LoggerFactory.getLogger(SerialiseringsTest.class);
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public SerialiseringsTest(String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( SerialiseringsTest.class );
    }

    /**
     * Rigourous Test :-)
     */
    public void testApp()
    {
        log.info("*** Demonstration *** Runs demonstration");
        String[] args = new String[0];
        Applikation.main(args);
    }
}
