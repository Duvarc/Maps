import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Wraps the parsing functionality of the MapDBHandler as an example.
 * You may choose to add to the functionality of this class if you wish.
 *
 * @author Alan Yao
 */

public class GraphDB {
    /**
     * Example constructor shows how to create and start an XML parser.
     *
     * @param db_path Path to the XML file to be parsed.
     */

    private HashMap<String, Node> nodes = new HashMap<String, Node>();
    private Connection ways;

    public GraphDB(String dbPath) {
        try {
            File inputFile = new File(dbPath);
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            MapDBHandler maphandler = new MapDBHandler(this);
            ways = maphandler.getWays();
            saxParser.parse(inputFile, maphandler);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        clean();
    }

    /**
     * Helper to process strings into their "cleaned" form, ignoring punctuation and capitalization.
     *
     * @param s Input string.
     * @return Cleaned string.
     */
    static String cleanString(String s) {
        return s.replaceAll("[^a-zA-Z ]", "").toLowerCase();
    }

    public void add(Node x) {
        nodes.put(String.valueOf(x.getId()), x);
    }

    public Node get(String id) {
        return nodes.get(id);
    }

    /**
     * Remove nodes with no connections from the graph.
     * While this does not guarantee that any two nodes in the remaining graph are connected,
     * we can reasonably assume this since typically roads are connected.
     */
    private void clean() {
        for (Iterator<HashMap.Entry<String, Node>> i = nodes.entrySet().iterator(); i.hasNext(); ) {
            HashMap.Entry<String, Node> entry = i.next();
            if (!ways.contains(entry.getKey())) {
                i.remove();
            }
        }
    }

    public HashMap<String, Node> getMap() {
        return nodes;
    }

    public Connection getWays() {
        return ways;
    }
}
