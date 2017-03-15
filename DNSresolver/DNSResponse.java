
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


// Lots of the action associated with handling a DNS query is processing
// the response. Although not required you might find the following skeleton of
// a DNSreponse helpful. The class below has bunch of instance data that typically needs to be 
// parsed from the response. If you decide to use this class keep in mind that it is just a 
// suggestion and feel free to add or delete methods to better suit your implementation as 
// well as instance variables.



public class DNSResponse {
    private int queryID;
	private int responseID;               // this is for the response it must match the one in the request
    private String queryName;
    private int queryType;
	private int answerCount = 0;          // number of answers
	private boolean decoded = false;      // Was this response successfully decoded
	private int nsCount = 0;              // number of nscount response records
	private int additionalCount = 0;      // number of additional (alternate) response records
	private boolean authoritative = false;// Is this an authoritative record
    private int rCode = 0;

	private byte[] data;
	private int length;
    private String serverIPAddress;

	private ByteBuffer buf;

    private List<ResponseRecord> recordList;
    private List<ResponseRecord> answerList;
    private List<ResponseRecord> nsList;
    private List<ResponseRecord> arList;

	// Note you will almost certainly need some additional instance variables.

	// When in trace mode you probably want to dump out all the relevant information in a response

	void dumpResponse() {
        System.out.print("\n\n");
        System.out.println("Query ID     " + Integer.toString(queryID) + " " + queryName + "  " + getQType() + " --> " + serverIPAddress);
        System.out.println("Response ID: " + Integer.toString(responseID) + " " + "Authoritative " + isAuthoritative());
        System.out.println("  Answers " + Integer.toString(answerCount));
        printTrace(answerList);
        System.out.println("  Nameservers " + Integer.toString(nsCount));
        printTrace(nsList);
        System.out.println("  Additional Information " + Integer.toString(additionalCount));
        printTrace(arList);
	}

	// The constructor: you may want to add additional parameters, but the two shown are
	// probably the minimum that you need.

	public DNSResponse (byte[] data, int len, int qID, String ipAddress) {
		this.data = data;
		length = len;
        queryID = qID;
        serverIPAddress = ipAddress;
		buf = ByteBuffer.wrap(data);
        recordList = new ArrayList<>();

		extract();
        extractRR();

        answerList = recordList.subList(0, answerCount);
        nsList = recordList.subList(answerCount, nsCount + answerCount);
        arList = recordList.subList(answerCount + nsCount, recordList.size());
	}

	// extract query portion of message
	private void extract() {
		responseID = buf.getShort() & 0xFFFF;
		short flags = buf.getShort();
        int auth = (flags >> 10) & 1;
        if (auth == 1) {
            authoritative = true;
        }
        rCode = flags & 0xF;

		short qdCount = buf.getShort();
		answerCount = buf.getShort();
		nsCount = buf.getShort();
		additionalCount = buf.getShort();

		queryName = getFQDN(buf.position(), 0);

        queryType = buf.getShort();
        short qClass = buf.getShort();
	}

	// extract resource record portion of message
	private void extractRR() {
        byte nextByte;
        short temp;
        int currentPos;
        int offset;
        short rType;
        short rClass;
        int TTL;
        short rdLength;
        String rData = "";
        String name;

        while (buf.hasRemaining()) {
            nextByte = buf.get();
            if (((nextByte >> 6) & 0x3) == 3) {
                temp = (short) ((nextByte << 8) + (buf.get() & 0xFF));
                offset = temp & 0x3FFF;
                currentPos = buf.position();
                name = getFQDN(offset, 0);
                buf.position(currentPos);
                rType = buf.getShort();
                rClass = buf.getShort();
                TTL = buf.getInt();
                rdLength = buf.getShort();
                if (rType == 2) {   // Nameserver
                    rData = getFQDN(buf.position(), rdLength);
                } else if (rType == 5) {    // CNAME
                    rData = getCNAME(buf.position(), rdLength);
                } else if (rType == 1) {    // IPv4
                    rData = getIPv4();
                } else if (rType == 28) {   // IPv6
                    rData = getIPv6();
                }

                ResponseRecord rRecord = new ResponseRecord(name, rType, rClass, TTL, rdLength, rData);
                recordList.add(rRecord);
            }
        }
    }

    /**
     * @param position position of the buffer pointer
     * @param rdLength length of data to read
     * @return  the fully qualified domain name
     *
     *
     */
	private String getFQDN(int position, int rdLength) {
        int firstPos = position;
        if (firstPos < buf.capacity()) {
            buf.position(firstPos);
        }
		String fqdn = "";
		byte nextByte = buf.get();
        int length = 0;
        char nextChar;
        short temp;
        int offset;
        int currentPos;
		while (nextByte != 0) {
			if ((nextByte >> 6) == 0) {
                length = nextByte;
				nextByte = buf.get();
			} else if (((nextByte >> 6) & 0x3) == 3) {
                temp = (short) ((nextByte << 8) + (buf.get() & 0xFF));
                offset = temp & 0x3FFF;
                currentPos = buf.position();
                fqdn += extractCompressed("", offset, currentPos);
                if (rdLength > 0) {
                    if (endOfData(currentPos, firstPos, rdLength)) {
                        buf.position(currentPos);
                        return fqdn;
                    }
                } else {
                    buf.position(currentPos);
                    return fqdn;
                }
                nextByte = buf.get();
                if (nextByte != 0) {
                    fqdn += ".";
                }
            } else {
                for (int i = 0; i < length; i++)  {
                    nextChar = (char) nextByte;
                    fqdn += nextChar;
                    nextByte = buf.get();
                }
                if (nextByte != 0) {
                    fqdn += ".";
                }
            }
		}
		return fqdn;
	}

    /**
     * @param compressed string to return used for recursive calls
     * @param offset     offset into DNS message
     * @param currentPos current position of buffer
     * @return           the compressed data
     *
     */
	private String extractCompressed(String compressed, int offset, int currentPos) {
        buf.position(offset);
        char nextChar;
        short temp;
        int off;
        byte nextByte = buf.get();
        while (nextByte != 0) {
            if ((nextByte >> 6) == 0) {
                length = nextByte;
                nextByte = buf.get();
            }  else if (((nextByte >> 6) & 0x3) == 3) {
                temp = (short) ((nextByte << 8) + (buf.get() & 0xFF));
                off = temp & 0x3FFF;
                return extractCompressed(compressed, off, buf.position());  // recursive call for compression within compression

            } else {
                for (int i = 0; i < length; i++) {
                    nextChar = (char) nextByte;
                    compressed += nextChar;
                    nextByte = buf.get();
                }
                if (nextByte != 0) {
                    compressed += ".";
                }
            }
        }
        buf.position(currentPos);
        return compressed;
    }

    /**
     * @param position current position of buffer
     * @param rdLength length of data to read
     * @return         data of a CNAME type record
     *
     */
    private String getCNAME(int position, int rdLength) {
        int firstPos = position;
        buf.position(firstPos);
        byte nextByte = buf.get();
        char nextChar;
        short temp;
        int offset;
        int currentPos;
        String cName = "";
        while (nextByte != 0) {
            if ((nextByte >> 6) == 0) {
                length = nextByte;
                nextByte = buf.get();
            } else if (((nextByte >> 6) & 0x3) == 3) {
                temp = (short) ((nextByte << 8) + (buf.get() & 0xFF));
                offset = temp & 0x3FFF;
                currentPos = buf.position();
                cName += extractCompressed("", offset, currentPos);
                if (endOfData(currentPos, firstPos, rdLength)) {
                    buf.position(currentPos);
                    return cName;
                }
                nextByte = buf.get();
                if (nextByte != 0) {
                    cName += ".";
                }
            } else {
                for (int i = 0; i < length; i++)  {
                    nextChar = (char) nextByte;
                    cName += nextChar;
                    nextByte = buf.get();
                }
                if (nextByte != 0) {
                    cName += ".";
                }
            }
        }
        return cName;
    }

    private String getIPv4() {
        byte[] address = new byte[4];
        for (int i = 0; i < address.length; i++) {
            address[i] = buf.get();
        }
        InetAddress ipAddress = null;
        try {
            ipAddress = InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            System.out.println("IP Address has illegal length.");
        }
        return ipAddress.getHostAddress();
    }

    private String getIPv6() {
        byte[] address = new byte[16];
        for (int i = 0; i < address.length; i++) {
            address[i] = buf.get();
        }
        InetAddress ipAddress = null;
        try {
            ipAddress = InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            System.out.println("IP Address has illegal length.");
        }
        return ipAddress.getHostAddress();
    }

	// You will also want methods to extract the response records and record
	// the important values they are returning. Note that an IPV6 reponse record
	// is of type 28. It probably wouldn't hurt to have a response record class to hold
	// these records.


    private boolean endOfData(int currentPos, int firstPos, int rdLength) {
        return currentPos == firstPos + rdLength;
    }

    public boolean isAuthoritative() {
        return authoritative;
    }

    public String getQueryName() {
        return queryName;
    }

    public String getQType() {
        if (queryType == 1) {
            return "A";
        } else {
            return "AAAA";
        }
    }

    public int getResponseID() {
        return responseID;
    }

    public int getRCode() {
        return rCode;
    }

    public int getAnswerCount() {
        return answerCount;
    }

    public int getNSCount() {
        return nsCount;
    }

    public int getAdditionalCount() {
        return additionalCount;
    }

    public List<ResponseRecord> getRecordList() {
        return recordList;
    }

    public List<ResponseRecord> getAnswerList() {
        return answerList;
    }

    public List<ResponseRecord> getNsList() {
        return nsList;
    }

    public List<ResponseRecord> getArList() {
        return arList;
    }

    private void printTrace(List<ResponseRecord> rList) {
        String recordName;
        int ttl;
        String recordType;
        String recordValue;
        for (ResponseRecord record : rList) {
            recordName = record.getName();
            ttl = record.getTTL();
            recordType = record.getrType();
            recordValue = record.getrData();
            System.out.format("       %-30s %-10d %-4s %s\n", recordName, ttl, recordType, recordValue);
        }
    }
}


