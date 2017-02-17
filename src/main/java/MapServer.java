import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.awt.BasicStroke;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import javax.imageio.ImageIO;

/* Maven is used to pull in these dependencies. */
import com.google.gson.Gson;

import static spark.Spark.*;

/**
 * This MapServer class is the entry point for running the JavaSpark web server for the BearMaps
 * application project, receiving API calls, handling the API call processing, and generating
 * requested images and routes.
 *
 * @author Alan Yao
 */
public class MapServer {
    /**
     * The root upper left/lower right longitudes and latitudes represent the bounding box of
     * the root tile, as the images in the img/ folder are scraped.
     * Longitude == x-axis; latitude == y-axis.
     */
    public static final double ROOT_ULLAT = 37.892195547244356, ROOT_ULLON = -122.2998046875,
            ROOT_LRLAT = 37.82280243352756, ROOT_LRLON = -122.2119140625;
    /**
     * Each tile is 256x256 pixels.
     */
    public static final int TILE_SIZE = 256;
    /**
     * Route stroke information: typically roads are not more than 5px wide.
     */
    public static final float ROUTE_STROKE_WIDTH_PX = 5.0f;
    /**
     * Route stroke information: Cyan with half transparency.
     */
    public static final Color ROUTE_STROKE_COLOR = new Color(108, 181, 230, 200);
    /**
     * HTTP failed response.
     */
    private static final int HALT_RESPONSE = 403;
    /**
     * The tile images are in the IMG_ROOT folder.
     */
    private static final String IMG_ROOT = "img/";
    /**
     * The OSM XML file path. Downloaded from <a href="http://download.bbbike.org/osm/">here</a>
     * using custom region selection.
     **/
    private static final String OSM_DB_PATH = "berkeley.osm";
    /**
     * Each raster request to the server will have the following parameters
     * as keys in the params map accessible by,
     * i.e., params.get("ullat") inside getMapRaster(). <br>
     * ullat -> upper left corner latitude,<br> ullon -> upper left corner longitude, <br>
     * lrlat -> lower right corner latitude,<br> lrlon -> lower right corner longitude <br>
     * w -> user viewport window width in pixels,<br> h -> user viewport height in pixels.
     **/
    private static final String[] REQUIRED_RASTER_REQUEST_PARAMS = {"ullat", "ullon", "lrlat",
            "lrlon", "w", "h"};
    /**
     * Each route request to the server will have the following parameters
     * as keys in the params map.<br>
     * start_lat -> start point latitude,<br> start_lon -> start point longitude,<br>
     * end_lat -> end point latitude, <br>end_lon -> end point longitude.
     **/
    private static final String[] REQUIRED_ROUTE_REQUEST_PARAMS = {"start_lat", "start_lon",
             "end_lat", "end_lon"};
    /* Define any static variables here. Do not define any instance variables of MapServer. */
    private static GraphDB g;

    private static QuadTree quad;
    private static QuadNode root;

    private static List<QuadNode> tiles;
    private static LinkedList<Long> route;


    private static int c = 0;
    private static HashSet<SearchNode> visited;
    private static HashMap<SearchNode, Double> dist;
    private static HashMap<SearchNode, SearchNode> prev;
    private static PriorityQueue<SearchNode> fringe;
    private static Connection neighbors;

    private static Node endNode;
    private static HashMap<SearchNode, Double> distToEnd;

    /**
     * Place any initialization statements that will be run before the server main loop here.
     * Do not place it in the main function. Do not place initialization code anywhere else.
     * This is for testing purposes, and you may fail tests otherwise.
     **/
    public static void initialize() {
        g = new GraphDB(OSM_DB_PATH);
        root = new QuadNode(0, ROOT_ULLAT, ROOT_ULLON, ROOT_LRLAT, ROOT_LRLON);
        quad = Utils.generateQuadTree(root, 8);
        neighbors = g.getWays();
        route = new LinkedList<Long>();
    }

    public static void main(String[] args) {
        initialize();
        staticFileLocation("/page");
        /* Allow for all origin requests (since this is not an authenticated server, we do not
         * care about CSRF).  */
        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Request-Method", "*");
            response.header("Access-Control-Allow-Headers", "*");
        });

        /* Define the raster endpoint for HTTP GET requests. I use anonymous functions to define
         * the request handlers. */
        get("/raster", (req, res) -> {
            HashMap<String, Double> params =
                    getRequestParams(req, REQUIRED_RASTER_REQUEST_PARAMS);
            /* The png image is written to the ByteArrayOutputStream */
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            /* getMapRaster() does almost all the work for this API call */
            Map<String, Object> rasteredImgParams = getMapRaster(params, os);
            /* On an image query success, add the image data to the response */
            if (rasteredImgParams.containsKey("query_success")
                    && (Boolean) rasteredImgParams.get("query_success")) {
                String encodedImage = Base64.getEncoder().encodeToString(os.toByteArray());
                rasteredImgParams.put("b64_encoded_image_data", encodedImage);
            }
            /* Encode response to Json */
            Gson gson = new Gson();
            return gson.toJson(rasteredImgParams);
        });

        /* Define the routing endpoint for HTTP GET requests. */
        get("/route", (req, res) -> {
            HashMap<String, Double> params =
                    getRequestParams(req, REQUIRED_ROUTE_REQUEST_PARAMS);
            route = findAndSetRoute(params);
            return !route.isEmpty();
        });

        /* Define the API endpoint for clearing the current route. */
        get("/clear_route", (req, res) -> {
            clearRoute();
            return true;
        });

        /* Define the API endpoint for search */
        get("/search", (req, res) -> {
            Set<String> reqParams = req.queryParams();
            String term = req.queryParams("term");
            Gson gson = new Gson();
            /* Search for actual location data. */
            if (reqParams.contains("full")) {
                List<Map<String, Object>> data = getLocations(term);
                return gson.toJson(data);
            } else {
                /* Search for prefix matching strings. */
                List<String> matches = getLocationsByPrefix(term);
                return gson.toJson(matches);
            }
        });

        /* Define map application redirect */
        get("/", (request, response) -> {
            response.redirect("/map.html", 301);
            return true;
        });
    }

    /**
     * Validate & return a parameter map of the required request parameters.
     * Requires that all input parameters are doubles.
     *
     * @param req            HTTP Request
     * @param requiredParams TestParams to validate
     * @return A populated map of input parameter to it's numerical value.
     */
    private static HashMap<String, Double> getRequestParams(
            spark.Request req, String[] requiredParams) {
        Set<String> reqParams = req.queryParams();
        HashMap<String, Double> params = new HashMap<>();
        for (String param : requiredParams) {
            if (!reqParams.contains(param)) {
                halt(HALT_RESPONSE, "Request failed - parameters missing.");
            } else {
                try {
                    params.put(param, Double.parseDouble(req.queryParams(param)));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    halt(HALT_RESPONSE, "Incorrect parameters - provide numbers.");
                }
            }
        }
        return params;
    }


    /**
     * Handles raster API calls, queries for tiles and rasters the full image. <br>
     * <p>
     * The rastered photo must have the following properties:
     * <ul>
     * <li>Has dimensions of at least w by h, where w and h are the user viewport width
     * and height.</li>
     * <li>The tiles collected must cover the most longitudinal distance per pixel
     * possible, while still covering less than or equal to the amount of
     * longitudinal distance per pixel in the query box for the user viewport size. </li>
     * <li>Contains all tiles that intersect the query bounding box that fulfill the
     * above condition.</li>
     * <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     * <li>If a current route exists, lines of width ROUTE_STROKE_WIDTH_PX and of color
     * ROUTE_STROKE_COLOR are drawn between all nodes on the route in the rastered photo.
     * </li>
     * </ul>
     * Additional image about the raster is returned and is to be included in the Json response.
     * </p>
     *
     * @param params Map of the HTTP GET request's query parameters - the query bounding box and
     *               the user viewport width and height.
     * @param os     An OutputStream that the resulting png image should be written to.
     * @return A map of parameters for the Json response as specified:
     * "raster_ul_lon" -> Double, the bounding upper left longitude of the rastered image <br>
     * "raster_ul_lat" -> Double, the bounding upper left latitude of the rastered image <br>
     * "raster_lr_lon" -> Double, the bounding lower right longitude of the rastered image <br>
     * "raster_lr_lat" -> Double, the bounding lower right latitude of the rastered image <br>
     * "raster_width"  -> Double, the width of the rastered image <br>
     * "raster_height" -> Double, the height of the rastered image <br>
     * "depth"         -> Double, the 1-indexed quadtree depth of the nodes of the rastered image.
     * Can also be interpreted as the length of the numbers in the image string. <br>
     * "query_success" -> Boolean, whether an image was successfully rastered. <br>
     * @see #REQUIRED_RASTER_REQUEST_PARAMS
     */
    public static Map<String, Object> getMapRaster(Map<String, Double> params, OutputStream os) {

        HashMap<String, Object> rasteredImageParams = new HashMap<String, Object>();

        double paramsUllat = params.get("ullat");
        double paramsUllon = params.get("ullon");
        double paramsLrlat = params.get("lrlat");
        double paramsLrlon = params.get("lrlon");

        double dpp = (paramsLrlon - paramsUllon) / params.get("w");

        tiles = new ArrayList<QuadNode>();

        if (!intersects(paramsUllat, paramsUllon, paramsLrlat, paramsLrlon, root)) {
            rasteredImageParams.put("query_success", false);
            return rasteredImageParams;
        }

        buildRaster(paramsUllat, paramsUllon, paramsLrlat, paramsLrlon, quad, dpp);

        Collections.sort(tiles);
        //System.out.println(tiles);

        double wDensity = Math.abs((tiles.get(0).getLrlon() - tiles.get(0).getUllon()) / 256);
        double hDensity = Math.abs((tiles.get(0).getUllat() - tiles.get(0).getLrlat()) / 256);

        double rasterWidth = Math.abs((tiles.get(tiles.size() - 1).getLrlon() - tiles.get(0).getUllon())
                / wDensity);
        double rasterHeight = Math.abs((tiles.get(0).getUllat() -
                tiles.get(tiles.size() - 1).getLrlat()) / hDensity);

        rasteredImageParams.put("raster_ul_lat", tiles.get(0).getUllat());
        rasteredImageParams.put("raster_ul_lon", tiles.get(0).getUllon());
        rasteredImageParams.put("raster_lr_lat", tiles.get(tiles.size() - 1).getLrlat());
        rasteredImageParams.put("raster_lr_lon", tiles.get(tiles.size() - 1).getLrlon());
        rasteredImageParams.put("raster_width", (int) (rasterWidth + 0.5));
        rasteredImageParams.put("raster_height", (int) (rasterHeight + 0.5));

        int depth = 0;
        if (tiles.get(0).getName() != 0) {
            depth = (int) Math.log10(tiles.get(0).getName()) + 1;
        }

        rasteredImageParams.put("depth", depth);

        BufferedImage result = new BufferedImage((int) (rasterWidth + 0.5),
                (int) (rasterHeight + 0.5), BufferedImage.TYPE_INT_RGB);
        Graphics gfx = result.getGraphics();

        try {
            int x = 0;
            int y = 0;
            for (QuadNode q : tiles) {
                File img = new File("img/" + String.valueOf(q.getName() + ".png"));
                BufferedImage bi = ImageIO.read(img);
                gfx.drawImage(bi, x, y, null);
                x += 256;
                if (x >= result.getWidth()) {
                    x = 0;
                    y += bi.getHeight();
                }
            }

            Graphics2D g2d = (Graphics2D) gfx;
            if (!route.isEmpty()) {
                HashMap<String, Node> map = g.getMap();
                long p = -1;
                QuadNode upperLeft = tiles.get(0);
                Node prev = map.get(String.valueOf(p));
                Node cur = prev;
                for (long n : route) {
                    if (p != -1) {
                        prev = map.get(String.valueOf(p));
                        cur = map.get(String.valueOf(n));

                        int x1 = (int) ((prev.getLon() - upperLeft.getUllon())  / wDensity);
                        int y1 = (int) ((upperLeft.getUllat() - prev.getLat()) / hDensity);
                        int x2 = (int) ((cur.getLon() - upperLeft.getUllon()) / wDensity);
                        int y2 = (int) ((upperLeft.getUllat() - cur.getLat()) / hDensity);

                        g2d.setStroke(new BasicStroke(MapServer.ROUTE_STROKE_WIDTH_PX,
                                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2d.setColor(MapServer.ROUTE_STROKE_COLOR);
                        g2d.drawLine(x1, y1, x2, y2);
                    }
                    p = n;
                }
            }


            ImageIO.write(result, "png", os);
            rasteredImageParams.put("query_success", true);
            return rasteredImageParams;
        } catch (IOException e) {
            System.out.println("There's a problem");
            rasteredImageParams.put("query_success", false);
            return rasteredImageParams;
        }
    }

    public static void buildRaster2(double ullat, double ullon, double lrlat,
                                    double lrlon, QuadTree tree, double res) {

        if (tree == null || tree.getRoot() == null || tree.topLeft() == null) {
            return;
        }

        double density = (tree.getRoot().getLrlon() - tree.getRoot().getUllon()) / 256;
        //System.out.println(density);

        if (density <= res) {
            tiles.add(tree.getRoot());
            return;
        }

        //Top left
        if (intersects(ullat, ullon, lrlat, lrlon, tree.topLeft().getRoot())) {
            buildRaster(ullat, ullon, lrlat, lrlon, tree.topLeft(), res);
        }
        //Top right
        if (intersects(ullat, ullon, lrlat, lrlon, tree.topRight().getRoot())) {
            buildRaster(ullat, ullon, lrlat, lrlon, tree.topRight(), res);
        }
        //Bottom left
        if (intersects(ullat, ullon, lrlat, lrlon, tree.bottomLeft().getRoot())) {
            buildRaster(ullat, ullon, lrlat, lrlon, tree.bottomLeft(), res);
        }
        //Bottom right
        if (intersects(ullat, ullon, lrlat, lrlon, tree.bottomRight().getRoot())) {
            buildRaster(ullat, ullon, lrlat, lrlon, tree.bottomRight(), res);
        }
    }

    public static void buildRaster(double ullat, double ullon, double lrlat, double lrlon, QuadTree tree, double res) {
        if (tree == null || tree.getRoot() == null) {
            return;
        }

        //System.out.println(density);

        if (intersects(ullat, ullon, lrlat, lrlon, tree.getRoot())) {
            double density = (tree.getRoot().getLrlon() - tree.getRoot().getUllon()) / 256;
            if (density <= res || tree.topLeft() == null) {
                tiles.add(tree.getRoot());
                return;
            }
            buildRaster(ullat, ullon, lrlat, lrlon, tree.topLeft(), res);
            buildRaster(ullat, ullon, lrlat, lrlon, tree.topRight(), res);
            buildRaster(ullat, ullon, lrlat, lrlon, tree.bottomLeft(), res);
            buildRaster(ullat, ullon, lrlat, lrlon, tree.bottomRight(), res);
        }
    }

    public static void gatherRaster(double ullat, double ullon, double lrlat, double lrlon, QuadTree tree, double res) {
        if (tree == null || tree.getRoot() == null) {
            return;
        }

        if (!intersects(ullat, ullon, lrlat, lrlon, tree.getRoot())) {
            return;
        }

        double density = (tree.getRoot().getLrlon() - tree.getRoot().getUllon()) / 256;

        if ((density <= res) || (int) (Math.log10(tree.getRoot().getName())) == 7) {
            tiles.add(quad.getRoot());
            return;
        }

        gatherRaster(ullat, ullon, lrlat, lrlon, tree.topLeft(), res);
        gatherRaster(ullat, ullon, lrlat, lrlon, tree.topRight(), res);
        gatherRaster(ullat, ullon, lrlat, lrlon, tree.bottomLeft(), res);
        gatherRaster(ullat, ullon, lrlat, lrlon, tree.bottomRight(), res);
    }

    /*public boolean tileContains(double ullat, double ullon, double lrlat, double lrlon, QuadTree x) {
        if (ullat <= x.getRoot().getUllat() && ullon >= x.getRoot().getUllon() &&
            lrlat >= x.getRoot().getLrlat() && lrlon =< x.getRoot().getLrlon()) {
                return true;
        }
    }*/

    public static boolean intersects(double ullat, double ullon, double lrlat, double lrlon, QuadNode x) {
        //Top to bottom
        if (ullat >= x.getLrlat() && lrlat <= x.getUllat()) {
            if (ullon <= x.getLrlon() && lrlon >= x.getUllon()) {
                return true;
            } else if (lrlon >= x.getUllon() && ullon <= x.getLrlon()) {
                return true;
            }
        } else if (lrlat <= x.getUllat() && ullat >= x.getLrlat()) {
            //Left to right
            if (ullon <= x.getLrlon() && lrlon >= x.getUllon()) {
                return true;
            } else if (lrlon >= x.getUllon() && ullon <= x.getLrlon()) {
                return true;
            }
        }
        return false;
    }

    public static boolean intersects2(double ullat, double ullon, double lrlat, double lrlon, QuadNode x) {
        if (ullon < x.getLrlon() && lrlon > x.getUllon() &&
                ullat < x.getLrlat() && lrlat > x.getUllat()) {
            return false;
        }
        return true;
    }

    /**
     * Searches for the shortest route satisfying the input request parameters, sets it to be the
     * current route, and returns a <code>LinkedList</code> of the route's node ids for testing
     * purposes. <br>
     * The route should start from the closest node to the start point and end at the closest node
     * to the endpoint. Distance is defined as the euclidean between two points (lon1, lat1) and
     * (lon2, lat2).
     *
     * @param params from the API call described in REQUIRED_ROUTE_REQUEST_PARAMS
     * @return A LinkedList of node ids from the start of the route to the end.
     */

    public static LinkedList<Long> findAndSetRoute(Map<String, Double> params) {

        clearRoute();

        visited = new HashSet<SearchNode>();
        dist = new HashMap<SearchNode, Double>();
        prev = new HashMap<SearchNode, SearchNode>();
        fringe = new PriorityQueue<SearchNode>();
        distToEnd = new HashMap<SearchNode, Double>();

        HashMap<String, Node> map = g.getMap();

        Connection neighbors = g.getWays();

        Node startNode = getClosestNode(params.get("start_lat"), params.get("start_lon"));
        Node end = getClosestNode(params.get("end_lat"), params.get("end_lon"));
        endNode = end;

        SearchNode v = new SearchNode(startNode, 0.0, null);
        SearchNode vv = new SearchNode(new Node(startNode.getId(), 400.0, 400.0), 2.3, v);
        dist.put(v, 0.0);
        fringe.add(v);

        System.out.println();

        System.out.println("Start Node -----> " + startNode.getId());
        System.out.println("End Node ------> " + end.getId());

        int count = 0;


       /* Node gg = map.get("760706748");
        for (String x : neighbors.get(String.valueOf(gg.getId()))) {
            Node m = map.get(x);
            System.out.println(m.getId() + ": " + getDistance(m, gg));
        }*/

        while (!fringe.isEmpty() && count < 5000000) {

            v = fringe.remove();
            //System.out.println(v.getNode().getId());
            //System.out.println(v);

            if (visited.contains(v)) {
                //continue;
                continue;
            }

            /*if (v.getNode().getId() == (Long.parseLong("760706748"))) {
                System.out.println(neighbors.get(String.valueOf(v.getNode().getId())));
                break;
            }*/

            visited.add(v);

            if (v.getNode() == end) {
                break;
            }

            //System.out.println(neighbors.get(String.valueOf(v.getNode().getId())));
            double toV = dist.get(v);

            for (String id : neighbors.get(String.valueOf(v.getNode().getId()))) {

                Node a = map.get(id);
                double distance = toV + getDistance(a, v.getNode());
                double cdist = v.getDist() + getDistance(a, v.getNode());

                if (cdist > distance) {
                    continue;
                }

                SearchNode c = new SearchNode(a, cdist, v);

                if (c.equals(v.getPrevious())) {
                    continue;
                }

                if (!dist.containsKey(c) || distance < dist.get(c)) {
                    dist.put(c, distance);
                    //visited.remove(c);
                    //fringe.remove(c);
                    fringe.add(c);
                    prev.put(c, v);
                }
            }
            count++;
        }


        LinkedList<Long> path = new LinkedList<Long>();
        while (v.getNode() != startNode) {
            path.addFirst(v.getNode().getId());
            v = prev.get(v);
        }
        path.addFirst(startNode.getId());

        System.out.println(path);
        return path;

        /*while (current.getNode().getId() != end.getId()) {

            current = fringe.remove();

            //System.out.println(current.getNode().getId());

            for (String id : neighbors.get(String.valueOf(current.getNode().getId()))) {
                Node a = map.get(id);
                double distance = current.getDist() + getDistance(a, current.getNode());
                SearchNode child = new SearchNode(a, distance, current);


                if (!visited.contains(child)) {
                    fringe.add(child);
                    visited.add(child);
                    dist.put(child, distance);
                } else if (dist.get(child) > distance) {
                    fringe.add(child);
                    dist.put(child, distance);
                }
            }

        }

        while (current != null) {
            route.addFirst(current.getNode().getId());
            current = current.getPrevious();
        }

        return route;*/
    }

    public static Node getEndNode() {
        return endNode;
    }

    public static double getDisToEnd(SearchNode n) {
        double dis;
        if (!distToEnd.containsKey(n)) {
            dis = getDistance(n.getNode(), endNode);
            distToEnd.put(n, dis);
            return dis;
        } else {
            return distToEnd.get(n);
        }
    }

    public static double getDis(SearchNode n) {
        return dist.get(n);
    }


    /**
     * Clear the current found route, if it exists.
     */
    public static void clearRoute() {
        route = new LinkedList<Long>();
    }

    public static Node getClosestNode(double lat, double lon) {
        HashMap<String, Node> map = g.getMap();
        double minDistance = 9999999;
        String minNode = "";

        for (Map.Entry<String, Node> entry : map.entrySet()) {
            Node n = entry.getValue();
            double currentDistance = distanceFromNode(n, lat, lon);
            if (currentDistance < minDistance) {
                minDistance = currentDistance;
                minNode = entry.getKey();
            }
        }
        return map.get(minNode);
    }

    /**
     * In linear time, collect all the names of OSM locations that prefix-match the query string.
     *
     * @param prefix Prefix string to be searched for. Could be any case, with our without
     *               punctuation.
     * @return A <code>List</code> of the full names of locations whose cleaned name matches the
     * cleaned <code>prefix</code>.
     */
    public static List<String> getLocationsByPrefix(String prefix) {
        return new LinkedList<>();
    }

    /**
     * Collect all locations that match a cleaned <code>locationName</code>, and return
     * information about each node that matches.
     *
     * @param locationName A full name of a location searched for.
     * @return A list of locations whose cleaned name matches the
     * cleaned <code>locationName</code>, and each location is a map of parameters for the Json
     * response as specified: <br>
     * "lat" -> Number, The latitude of the node. <br>
     * "lon" -> Number, The longitude of the node. <br>
     * "name" -> String, The actual name of the node. <br>
     * "id" -> Number, The id of the node. <br>
     */

    public static double getDistance(Node a, Node b) {
        double xDist = b.getLon() - a.getLon();
        double yDist = b.getLat() - a.getLat();
        return Math.sqrt(xDist * xDist + yDist * yDist);
    }

    public static double distanceFromNode(Node n, double lat, double lon) {
        double xDist = n.getLon() - lon;
        double yDist = n.getLat() - lat;
        return Math.sqrt(xDist * xDist + yDist * yDist);
    }

    public static List<Map<String, Object>> getLocations(String locationName) {
        return new LinkedList<>();
    }
}
