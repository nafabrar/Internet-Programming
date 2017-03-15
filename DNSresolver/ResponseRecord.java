

public class ResponseRecord {
    private String name;
    private short rType;
    private short rClass;
    private int ttl;
    private short rLength;
    private String rData;



    /**
     * Represents a DNS resource record
     *
     * @param rName   	Resource Name
     * @param rType	Resource Type
     * @param rClass  	Resource Class
     * @param ttl	  	Resource Time to live
     * @param dLength 	Resource data length
     * @param d       	Resource data
     */
    public ResponseRecord(String rName, short rType, short rClass, int ttl, short dLength, String d) {
        this.name = rName;
        this.rType = rType;
        this.rClass = rClass;
        this.ttl = ttl;
        this.rLength = dLength;
        this.rData = d;
    }

    public String getName() {
        return name;
    }

    public String getrType() {
        switch (rType) {
            case 2:
                return "NS";
            case 5:
                return "CN";
            case 1:
                return "A";
            case 28:
                return "AAAA";
            default:
                return "A";
        }
    }

    public short getrClass() {
        return rClass;
    }

    public int getTTL() {
        return ttl;
    }

    public short getrLength() {
        return rLength;
    }

    public String getrData() {
        return rData;
    }
}
