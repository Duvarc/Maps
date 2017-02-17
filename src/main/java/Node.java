/**
 * Created by hung-weichuang on 4/10/16.
 */

import java.util.HashSet;
import java.util.Set;

public class Node implements Comparable<Node> {

    private long id;
    private double lat;
    private double lon;
    private String name;

    private HashSet<Connection> connectSet;

    public Node(long id, double lat, double lon) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
        connectSet = new HashSet<Connection>();
    }

    public Node(long id, double lat, double lon, String name) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
        this.name = name;
        connectSet = new HashSet<Connection>();
    }

    public long getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public void addConnection(Connection x) {
        connectSet.add(x);
    }

    public Set<Connection> getConnections() {
        return connectSet;
    }

    public String toString() {
        return id + " | " + lat + " | " + lon;
    }

    public int compareTo(Node other) {
        if (id > other.getId()) {
            return 1;
        } else if (other.getId() > id) {
            return -1;
        }
        return 0;
    }

    public boolean equals(Object other) {
        if (id == ((Node) other).getId()) {
            return true;
        }
        return false;
    }

    public int hashCode() {
        return (int) id;
    }

    /*public boolean equals(Node other) {
        if (id == ((Node) other).getId()) {
            return true;
        }
        return false;
    }*/

}
