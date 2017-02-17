public class Utils {

    public static QuadTree generateQuadTree(QuadNode parent, int depth) {
        if (depth == 0) {
            return null;
        }

        QuadNode topLeft = new QuadNode(parent.getName() * 10 + 1,
                parent.getUllat(),
                parent.getUllon(),
                (parent.getUllat() + parent.getLrlat()) / 2,
                (parent.getUllon() + parent.getLrlon()) / 2);

        QuadNode topRight = new QuadNode(parent.getName() * 10 + 2,
                parent.getUllat(),
                (parent.getUllon() + parent.getLrlon()) / 2,
                (parent.getLrlat() + parent.getUllat()) / 2,
                parent.getLrlon());

        QuadNode bottomLeft = new QuadNode(parent.getName() * 10 + 3,
                (parent.getUllat() + parent.getLrlat()) / 2,
                parent.getUllon(),
                parent.getLrlat(),
                (parent.getLrlon() + parent.getUllon()) / 2);

        QuadNode bottomRight = new QuadNode(parent.getName() * 10 + 4,
                (parent.getUllat() + parent.getLrlat()) / 2,
                (parent.getUllon() + parent.getLrlon()) / 2,
                parent.getLrlat(),
                parent.getLrlon());

        return new QuadTree(parent,
                generateQuadTree(topLeft, depth - 1),
                generateQuadTree(topRight, depth - 1),
                generateQuadTree(bottomLeft, depth - 1),
                generateQuadTree(bottomRight, depth - 1));
    }
}
