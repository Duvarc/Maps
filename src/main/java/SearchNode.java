/**
 * Created by hung-weichuang on 4/19/16.
 */


public class SearchNode implements Comparable<SearchNode> {

    private Node n;
    private double dist;
    private SearchNode prev;

    public SearchNode(Node n, double dist, SearchNode prev) {
        this.n = n;
        this.dist = dist;
        this.prev = prev;
    }

    public double getDist() {
        return dist;
    }

    public void setDist(double d) {
        dist = d;
    }

    public double getDistanceEnd() {
        return MapServer.getDistance(n, MapServer.getEndNode());
    }

    public Node getNode() {
        return n;
    }

    public SearchNode getPrevious() {
        return prev;
    }

    @Override
    public int compareTo(SearchNode other) {
        double dis = MapServer.getDis(this);
        double otherDis = MapServer.getDis(other);

        if (dist + MapServer.getDisToEnd(this) > other.getDist() + MapServer.getDisToEnd(other)) {
            return 1;
        } else if (other.getDist() + MapServer.getDisToEnd(other)
                > dist + MapServer.getDisToEnd(this)) {
            return -1;
        }
        return 0;
    }


    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        SearchNode other = (SearchNode) o;
        if (n.getId() == other.getNode().getId()) {
            return true;
        }
        return false;
    }

    public int hashCode() {
        return (int) n.getId();
    }

    /*public boolean equals(SearchNode other) {
        if (getNode().getId() == other.getNode().getId()) {
            return true;
        }
        return false;
    }*/

}
