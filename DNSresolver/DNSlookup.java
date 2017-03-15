
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 *
 */

/**
 * @author Donald Acton
 * This example is adapted from Kurose & Ross
 * Feel free to modify and rearrange code as you see fit
 */
public class DNSlookup {


    static final int MIN_PERMITTED_ARGUMENT_COUNT = 2;
    static final int MAX_PERMITTED_ARGUMENT_COUNT = 3;
    static int numQueries;
    static boolean tracingOn = false;
    static DNSResponse response; // Just to force compilation
    static ResponseRecord rRecord;
    static boolean IPV6Query = false;
    static InetAddress rootNameServer;
    static int defaultPort = 53;
    static byte[] sent;
    static String fqdn;
    static List<String> cNameList;
    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        int argCount = args.length;

        if (argCount < MIN_PERMITTED_ARGUMENT_COUNT || argCount > MAX_PERMITTED_ARGUMENT_COUNT) {
            usage();
            return;
        }

        rootNameServer = InetAddress.getByName(args[0]);
        fqdn = args[1];

        if (argCount == 3) {  // option provided
            if (args[2].equals("-t"))
                tracingOn = true;
            else if (args[2].equals("-6"))
                IPV6Query = true;
            else if (args[2].equals("-t6")) {
                tracingOn = true;
                IPV6Query = true;
            } else { // option present but wasn't valid option
                usage();
                return;
            }
        }
        cNameList = new ArrayList<>();
        queryLookup(fqdn, rootNameServer, -1, 0, 0);
    }

    /**
     *
     * @param domainName  	Domain Name we are looking for
     * @param dnsServer    The next server to query
     * @param qID  	    Query ID
     * @param numQueries	Number of queries
     * @param numTimeouts 	Timeout
     */
    public static String queryLookup(String domainName, InetAddress dnsServer, int qID, int numQueries, int numTimeouts) throws IOException {

        if (numQueries > 30) {  // too many queries attempted, exit
            System.out.println(domainName + " -3   " + "A " + "0.0.0.0");
            System.exit(1);
        }
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(5000);
        String[] qname = domainName.split("\\.");
        int queryID;
        if (qID >= 0) {
            queryID = qID;
        } else {
            Random rng = new Random();
            queryID = rng.nextInt(65536);
        }

        ByteBuffer buf = ByteBuffer.allocate(512);
        buf.putShort((short) queryID);
        buf.putShort((short) 0);  // QR, Opcode, AA, TC, RD, RA, Z, RCODE
        buf.putShort((short) 1);  // QDCOUNT
        buf.putShort((short) 0);  // ANCOUNT
        buf.putShort((short) 0);  // NSCOUNT
        buf.putShort((short) 0);  // ARCOUNT
        for (String name : qname) { // QNAME
            byte nameLength = (byte) name.length();
            byte[] nameBytes = name.getBytes();
            buf.put(nameLength);
            for (byte nameByte : nameBytes) {
                buf.put(nameByte);
            }
        }
        buf.put((byte) 0);  // end of QNAME
        if (IPV6Query) {
            if (domainName.equals(fqdn) || cNameList.contains(domainName)) {
                buf.putShort((short) 0x1C);  // QTYPE
            } else {
                buf.putShort((short) 1);
            }
        } else {
            buf.putShort((short) 1);
        }
        buf.putShort((short) 1);  // QCLASS

        DatagramPacket packet = new DatagramPacket(buf.array(), buf.position(), dnsServer, defaultPort);

        socket.send(packet);

        byte[] received = new byte[1024];
        packet = new DatagramPacket(received, received.length);
        try {
            socket.receive(packet);
        } catch (SocketTimeoutException e) {
            numTimeouts++;
            if (numTimeouts >= 2) { // quit if more than 2 consecutive timeouts
                System.out.println(domainName + " -2   " + "A " + "0.0.0.0");
                System.exit(1);
            }
            return queryLookup(domainName, dnsServer, queryID, ++numQueries, numTimeouts);
        }

        response = new DNSResponse(received, received.length, queryID, dnsServer.getHostAddress());

        while (response.getResponseID() != queryID) {
                socket.receive(packet);
        }

        if (tracingOn) {
            response.dumpResponse();
        }

        switch (response.getRCode()) {
            case 3: // no such name
                System.out.println(domainName + " -1   " + "A " + "0.0.0.0");
                System.exit(1);
                break;
            case 1:
            case 2:
            case 4:
            case 5:
                System.out.println(domainName + " -4   " + "A " + "0.0.0.0");
                System.exit(1);
                break;
            case 0:
                if (response.isAuthoritative() && response.getAnswerCount() == 0) {
                    System.out.println(domainName + " -6   " + "A " + "0.0.0.0");
                    System.exit(1);
                    break;
                }
        }

        if (response.isAuthoritative()) {
            if (isFinalAnswer()) {
                if (!containsCName()) {
                    getAnswer(fqdn);    // authoritative answer that is not a CNAME
                } else {
                    resolveCName();
                }
            } else {
                if (containsCName()) {
                    resolveCName();
                } else {
                    String nextAddress = response.getAnswerList().get(0).getrData();
                    return queryLookup(fqdn, InetAddress.getByName(nextAddress), -1, ++numQueries, 0);
                }
            }
        } else {
            List<ResponseRecord> nsList = response.getNsList();
            if (!nsList.isEmpty()) {
                String nextServer = response.getNsList().get(0).getrData();
                String nextAddress = findAdditionalIPAddress(nextServer);
                if (nextAddress != null) {
                    return queryLookup(domainName, InetAddress.getByName(nextAddress), -1, ++numQueries, 0);
                } else {
                    return queryLookup(nextServer, rootNameServer, -1, ++numQueries, 0);
                }
            } else {
                if (containsCName()) {
                    resolveCName();
                }
            }
        }

        return null;
    }

    /**
     * @param domainName String representing a domain name
     *                    Prints the answer (domainName, Time to live, address) in the console
     */
    private static void getAnswer(String domainName) {
        int TTL;
        String type;
        String address;
        for (ResponseRecord record : response.getAnswerList()) {
            TTL = record.getTTL();
            type = record.getrType();
            address = record.getrData();
            System.out.println(domainName + " " + TTL + " " + type + " " + address);
        }
    }

    private static boolean isFinalAnswer() {
        for (ResponseRecord record : response.getAnswerList()) {
            if (!cNameList.isEmpty()) {
                for (String cName : cNameList) {
                    if (record.getName().equals(cName) && record.getrType().equals("A") || record.getrType().equals("AAAA")) {
                        return true;
                    }
                }
            } else {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCName() {
        for (ResponseRecord record : response.getAnswerList()) {
            if (record.getrType().equals("CN")) {
                cNameList.add(record.getrData());
                return true;
            }
        }
        return false;
    }

    private static String resolveCName() throws IOException {
        String nextServer = response.getAnswerList().get(0).getrData();
        return queryLookup(nextServer, rootNameServer, -1, ++numQueries, 0);
    }

    /**
     * @param serverName  a server in the Nameservers section
     * @return            IPv4 address of the server if found
     */
    private static String findAdditionalIPAddress(String serverName) {
        for (ResponseRecord record : response.getArList()) {
            if (record.getName().equals(serverName) && record.getrType().equals("A")) {
                return record.getrData();
            }
        }
        return null;
    }

    private static void usage() {
        System.out.println("Usage: java -jar DNSlookup.jar rootDNS name [-6|-t|t6]");
        System.out.println("   where");
        System.out.println("       rootDNS - the IP address (in dotted form) of the root");
        System.out.println("                 DNS server you are to start your search at");
        System.out.println("       name    - fully qualified domain name to lookup");
        System.out.println("       -6      - return an IPV6 address");
        System.out.println("       -t      - trace the queries made and responses received");
        System.out.println("       -t6     - trace the queries made, responses received and return an IPV6 address");
    }
}


