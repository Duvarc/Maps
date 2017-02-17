/**
 * Created by hung-weichuang on 4/10/16.
 */

import java.util.HashMap;
import java.util.HashSet;

public class Connection {

    private HashMap<String, HashSet<String>> connections;

    public Connection() {
        connections = new HashMap<String, HashSet<String>>();
    }

    public void add(String first, String second) {

        if (!connections.containsKey(second) && !connections.containsKey(first)) {
            HashSet<String> list = new HashSet<String>();
            list.add(second);
            connections.put(first, list);

            HashSet<String> list2 = new HashSet<String>();
            list2.add(first);
            connections.put(second, list2);
        } else if (!connections.containsKey(second) && connections.containsKey(first)) {
            HashSet<String> list = new HashSet<String>();
            list.add(first);
            connections.put(second, list);

            connections.get(first).add(second);
        } else if (connections.containsKey(second) && !connections.containsKey(first)) {
            HashSet<String> list = new HashSet<String>();
            list.add(second);
            connections.put(first, list);

            connections.get(second).add(first);
        } else {
            connections.get(second).add(first);
            connections.get(first).add(second);
        }
    }

    public HashSet<String> get(String id) {
        return connections.get(id);
    }

    public boolean contains(String id) {
        return connections.containsKey(id);
    }

    public int size() {
        return connections.size();
    }

    public String toString() {
        /*String s = "";
        Iterator it = connections.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            s+= "\n" + pair.getKey() + " = " + pair.getValue();
       } */

        return "d";

    }
}
