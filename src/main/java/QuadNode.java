/**
 * Created by hung-weichuang on 4/10/16.
 */

import java.io.File;

public class QuadNode implements Comparable<QuadNode> {

    private int name;
    private double ullat, ullon, lrlat, lrlon;

    public QuadNode(int name, double ullat, double ullon, double lrlat, double lrlon) {
        this.name = name;
        this.ullat = ullat;
        this.ullon = ullon;
        this.lrlat = lrlat;
        this.lrlon = lrlon;
    }

    public int getName() {
        return name;
    }

    public double getUllat() {
        return ullat;
    }

    public double getUllon() {
        return ullon;
    }

    public double getLrlat() {
        return lrlat;
    }

    public double getLrlon() {
        return lrlon;
    }

    public String getFileName() {
        return Integer.toString(name);
    }

    public File getImage() {
        String path = "img/" + getFileName();
        return new File(path);
    }

    public int compareTo(QuadNode other) {
        if (this.ullat < other.getUllat()) {
            return 1;
        } else if (this.ullat > other.getUllat()) {
            return -1;
        } else {
            if (this.ullon > other.getUllon()) {
                return 1;
            } else if (this.ullon < other.getUllon()) {
                return -1;
            }
            return 0;
        }
    }

    public String toString() {
        return String.valueOf(name);
        //return "1) " + ullat + " 2) " + ullon + " 3) " + lrlat + " 4) " + lrlon;
    }
}
