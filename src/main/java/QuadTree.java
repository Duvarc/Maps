/**
 * Created by hung-weichuang on 4/10/16.
 */
public class QuadTree {

    private QuadNode root;
    private QuadTree ul, ur, lr, ll;

    public QuadTree(QuadNode root, QuadTree ul, QuadTree ur, QuadTree ll, QuadTree lr) {
        this.root = root;
        this.ul = ul;
        this.ur = ur;
        this.ll = ll;
        this.lr = lr;
    }

    public QuadNode getRoot() {
        return root;
    }

    public void setRoot(QuadNode root) {
        this.root = root;
    }

    public QuadTree topLeft() {
        return ul;
    }

    public QuadTree topRight() {
        return ur;
    }

    public QuadTree bottomRight() {
        return lr;
    }

    public QuadTree bottomLeft() {
        return ll;
    }

    public QuadTree getUl() {
        return ul;
    }

    public void setUl(QuadTree ul) {
        this.ul = ul;
    }

    public QuadTree getUr() {
        return ur;
    }

    public QuadTree getLr() {
        return lr;
    }

    public void setLr(QuadTree lr) {
        this.lr = lr;
    }

    public QuadTree getLl() {
        return ll;
    }

    public void setLl(QuadTree ll) {
        this.ll = ll;
    }

    public String toString() {
        return String.valueOf(root.getName());
    }
}
