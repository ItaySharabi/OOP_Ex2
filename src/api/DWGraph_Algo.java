package api;

import com.google.gson.*;
import gameClient.util.Point3D;
import kotlin.jvm.internal.MagicApiIntrinsics;

import java.io.*;
import java.util.*;
import java.util.List;

/**
 * This class represents a set of graph theory algorithms to
 * apply on a directed, weighted graph data structure, including:
 * Saving and loading a graph, calculating shortest paths on the graph from
 * one node to another, checking if the graph is strongly connected, and so on...
 *
 *
 *
 */
public class DWGraph_Algo implements dw_graph_algorithms {

    private directed_weighted_graph graph;

    /**
     * Empty constructor.
     */
    public DWGraph_Algo() {
        graph = new DWGraph_DS();
    }

    /**
     * A constructor that initiates this.graph with given graph 'g'.
     * @param g - the graph to init this class with.
     */
    public DWGraph_Algo(directed_weighted_graph g) {
        init(g);
    }

    /**
     * Init the graph on which this set of algorithms operates on.
     *
     * @param g - the graph to init this class with.
     */
    @Override
    public void init(directed_weighted_graph g) {
        if (g != null) graph = g;
        else graph = new DWGraph_DS();
    }

    /**
     * Return the underlying graph of which this class works.
     *
     * @return - the current graph.
     */
    @Override
    public directed_weighted_graph getGraph() {
        return graph;
    }

    /**
     * Compute a deep copy of this weighted graph.
     * This is done by using all other copy constructors of a DWGraph_DS Object.
     * @return - a deep copy of this.graph.
     */
    @Override
    public directed_weighted_graph copy() {
        return new DWGraph_DS(this.graph);
    }

    /**
     * Returns true if and only if (iff) there is a valid path from each node to each
     * other node. NOTE: assume directional graph (all n*(n-1) ordered pairs).
     * This method is implemented using the Kosaraju's algorithm and a regular BFS algorithm.
     * More on the implementation is over the relevant methods.
     * @Runtime: Regular BFS + Kosaraju's algorithm(also uses bfs) = O(2*(|V|+|E|)) = O(|V|+|E|)
     * @return - true iff there's a valid path from every node to each other.
     */
    @Override
    public boolean isConnected() {
        if (graph.getV().size() <= 1) return true; //A graph with 0 or 1 nodes is a connected graph.

        node_data start = getFirstNode(graph);
        return isConnectedBFS(start) & Kosaraju(start); // Check is connected of graph and graph transpose on same start node.
    }

    /**
     * returns the length of the shortest path between src to dest
     * Note: if no such path --> returns -1
     * This method uses the shortestPath() method and receives the path returned.
     * The value returned is the last elements weight, which is the distance from
     * src to dest.
     * @Runtime: Regular BFS using a priority queue = O(|V|+|E|)
     * @param src  - start node
     * @param dest - end (target) node
     * @return - the distance between src and dest on the current graph.
     */
    @Override
    public double shortestPathDist(int src, int dest) {
        List<node_data> path = shortestPath(src, dest); //Execute a shortestPath Algo from src to dest.
        if (path == null) return -1;
        int size = path.size(); //Keep the size of the path.

        //If size > 0 -> There is a path from src to dest, return the total weight.
        if (size > 0) return path.get(size - 1).getWeight();

        return -1;
    }

    /**
     * returns the the shortest path between src to dest - as an ordered List of nodes:
     * src--> n1-->n2-->...dest
     * Logic only was taken from: https://en.wikipedia.org/wiki/Shortest_path_problem
     * Note if no such path --> returns null;
     * @Runtime: Regular BFS using a priority queue = O(|V|+|E|).
     * @param src  - start node
     * @param dest - end (target) node
     * @return - the path between src and dest if there is one.
     */
    @Override
    public List<node_data> shortestPath(int src, int dest) {
        resetTags();

        if (graph.getNode(src) == null || graph.getNode(dest) == null) return null;
        if (graph.getE(src).size() == 0) return null;
        if (src == dest) return new LinkedList<>();

        HashMap<Integer, node_data> prevNode = new HashMap<Integer, node_data>(); //A map of parent nodes. for (Integer) key, map (node_info) parent.

        PriorityQueue<node_data> pq = new PriorityQueue<node_data>(new NodeComparator()); //The BFS queue
        List<node_data> path = null;

        boolean destinationFound = false;
        node_data curr = null, neighbor = null;

        setWeightInfinity(); //Init all distances from node 'src' to infinity
        graph.getNode(src).setWeight(0); //The distance from src to src is 0.
        pq.add(graph.getNode(src)); //Add start node to the queue and start traversing.

        double totalDist;

        while (!pq.isEmpty()) {

            curr = pq.poll();

            for (edge_data outEdge : graph.getE(curr.getKey())) {
                neighbor = graph.getNode(outEdge.getDest());

                if (neighbor.getKey() == dest) destinationFound = true;
                totalDist = curr.getWeight() + outEdge.getWeight();

                if (totalDist < neighbor.getWeight()) { //If the total distance is less than the known distance from neighbor to src.
                    neighbor.setWeight(totalDist);
                    if (prevNode.putIfAbsent(neighbor.getKey(), curr) != null)//If the neighbor's key isn't associated with curr's node
                        prevNode.put(neighbor.getKey(), curr);// Update the current calling node in prevNode.
                }
                if (!pq.contains(neighbor) && neighbor.getTag() == 0) pq.add(neighbor);
            }
            curr.setTag(1);
        }

        if (destinationFound)
            path = rebuildPath(src, dest, prevNode);

        return path;
    }

    /**
     * Saves this weighted (directed) graph to the given.
     * file name - in JSON format.
     *
     * @param file - the file name (may include a relative path).
     * @return true - iff the file was successfully saved.
     */
    @Override
    public boolean save(String file) {

        Gson json = new GsonBuilder().create();
        JsonObject graph = new JsonObject();
        JsonArray nodes = new JsonArray();
        JsonArray edges = new JsonArray();


        for (node_data node : this.graph.getV()) {
            JsonObject v = new JsonObject();
            double x = node.getLocation().x(), y = node.getLocation().y(), z = node.getLocation().z();
            v.addProperty("pos", x + "," + y + "," + z);
            v.addProperty("id", node.getKey());
            nodes.add(v);

            for (edge_data outEdge : this.graph.getE(node.getKey())) {
                JsonObject edge = new JsonObject();
                edge.addProperty("src", outEdge.getSrc());
                edge.addProperty("w", outEdge.getWeight());
                edge.addProperty("dest", outEdge.getDest());
                edges.add(edge);
            } //Add all edges to the JSONArray Object
        } //Add all nodes to the JSONArray Object

        graph.add("Edges", edges);
        graph.add("Nodes", nodes);

        File f = new File(file);
        try {
            FileWriter writer = new FileWriter(f);
            writer.write(json.toJson(graph));
            writer.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to save graph");
            return false;
        }
    }

    /**
     * This method load a graph to this graph algorithm's class.
     * If the file was successfully loaded - the underlying graph
     * of this class will be changed (to the loaded one), in case the
     * graph was not loaded the original graph should remain "as is".
     *
     * @param file - file name of JSON file.
     * @return true - iff the graph was successfully loaded.
     */
    @Override
    public boolean load(String file) {

        directed_weighted_graph newGraph = new DWGraph_DS(); //The graph to load onto.
        JsonObject graph; //The Gson Object to read from.
        File f = new File(file); //The file containing the data.
        try {
            FileReader reader = new FileReader(f);
            graph = new JsonParser().parse(reader).getAsJsonObject();
            JsonArray edges = graph.getAsJsonArray("Edges"); //Get The "Edges" member from the Json Value.
            JsonArray nodes = graph.getAsJsonArray("Nodes"); //Get The "Edges" member from the Json Value.

            for (JsonElement node : nodes) {

                int key = ((JsonObject) node).get("id").getAsInt();
                double x,y,z;
                String pos = ((JsonObject) node).get("pos").getAsString();
                String[] posArr = pos.split(",");
                x = Double.parseDouble(posArr[0]);
                y = Double.parseDouble(posArr[1]);
                z = Double.parseDouble(posArr[2]);
                geo_location location = new Point3D(x, y, z);
                node_data n = new NodeData(key); //Insert into node_data n values from Json.
                n.setLocation(location); //Insert into node_data n location values from Json that created.
                newGraph.addNode(n);
            }

            for (JsonElement edge : edges) {
                int src = ((JsonObject) edge).get("src").getAsInt(); //Receive src
                double weight = ((JsonObject) edge).get("w").getAsDouble(); //Receive weight
                int dest = ((JsonObject) edge).get("dest").getAsInt(); //Receive dest

                edge_data e = new EdgeData(src, dest, weight); //Build a new edge with given args
                newGraph.connect(e.getSrc(), e.getDest(), e.getWeight()); //Connect between nodes on the new graph
            }
            this.graph = newGraph;
            System.out.println("Graph loaded successfully");
            return true;

        } catch (FileNotFoundException e) {
            System.out.println("Failed to load graph");
            e.printStackTrace();
        }

        return false;
    }

    /**
     * resets all tags to 0.
     */
    private void resetTags() {
        for (node_data n : graph.getV())
            n.setTag(0);
    }

    /**
     * This method transposes the given graph g.
     * The new graph will have the same set of vertices V = {v1, v2, .. , v(n)},
     * And all transposed edges. E = {(v1,v2), (v2,v6), .. }, E(transposed) = {(v2,v1), (v6,v2), ..}.
     * @param g - the given graph.
     * @return a transposed directed_weighted_graph.
     */
    private directed_weighted_graph transpose (directed_weighted_graph g) {
        if (g == null) return null;

        directed_weighted_graph transposed = new DWGraph_DS();

        for (node_data node : g.getV()) transposed.addNode(new NodeData(node)); //Add all nodes to the graph.

        for (node_data node : g.getV()) //For each node in the original graph
            for (edge_data edge : g.getE(node.getKey())) //Iterate over all outgoing edges
                transposed.connect(edge.getDest(), edge.getSrc(), edge.getWeight());//And connect upside-down.

        return transposed;
    }

    /**
     * This method checks if all nodes on the graph have been visited.
     * If one node was not visited then returns false - meaning one node was
     * not reached through another node.
     *
     * @param graph - this graph.
     * @return true iff all nodes on the graph were visited.
     */
    private boolean allNodesWereVisited(directed_weighted_graph graph) {
        for (node_data n : graph.getV())
            if (n.getTag() == 0) return false;

        return true;
    }

    /**
     * Set all weights to infinity.
     */
    private void setWeightInfinity() {

        for (node_data n : graph.getV()) n.setWeight(Double.MAX_VALUE + 1);
    }

    /**
     * This methods rebuilds the path from node src to node dest.
     * This map 'prevNode' holds the information to build the path,
     * of which node was called from which node.
     * @param src - the beginning of the list.
     * @param dest - the end of the list.
     * @param prevNode - info.
     * @return a list containing all nodes on the shortest path from src to dest.
     */
    private List<node_data> rebuildPath(int src, int dest, HashMap<Integer, node_data> prevNode) {
        List<node_data> path = new LinkedList<node_data>();

        node_data current = graph.getNode(dest), prev = null;
        current.setTag(0);
        path.add(current);

        while (current.getKey() != src) {
            prev = prevNode.get(current.getKey()); //Extract the node who called current node.
            prev.setTag(0); //Set tag to 0 (resetTags()) because nodes are deep copied with tag == 1.
            path.add(graph.getNode(prev.getKey())); //Add a deep copy of 'prev' to the list.
            current = prev; //Increment current node.
        }

        Collections.reverse(path);
        return path; //Return a path with all copied nodes on the requested path.
    }


    /**
     * This method uses the private methods transpose() and isConnectedBFS()
     * to transpose the this.graph and re-execute isConnectedBFS() on the same graph.
     * @Runtime: Graph transposition + regular BFS = O(2*(|V|+|E|)) = O(|V|+|E|)
     * @param start - BFS the the transposed graph for node start.
     * @return - true iff all nodes have been visited after one graph traverse.
     */
    private boolean Kosaraju(node_data start) {
        boolean isConnected;
        directed_weighted_graph transposed = transpose(graph);

        directed_weighted_graph temp = getGraph();
        graph = transposed;
        isConnected = isConnectedBFS(start);
        graph = temp;
        return isConnected;
    }

    /**
     * Explore the graph Breadth-First and mark all nodes passed by as visited.
     * if all nodes of the graph were visited in 1 executions, this method returns true.
     * @Runtime: Regular graph traversal - O(|V|+|E|).
     * @param start - the node to start traversing from.
     * @return true or false, if all nodes could be reached from start node.
     */
    private boolean isConnectedBFS(node_data start) {
        resetTags();// Was added to make sure all node's tags are reset.
        node_data curr = start;
        node_data neighbor;
        Queue<node_data> queue = new LinkedList<>();
        queue.add(curr);
        curr.setTag(1);

        while (!queue.isEmpty()) {
            curr = queue.poll();
            Collection<edge_data> outEdges = graph.getE(curr.getKey());
            if (outEdges.size() == 0)
                return false; //If a single node has no outgoing edges - the graph is not connected.
            for (edge_data e : outEdges) { //Iterate over outgoing edges from curr
                neighbor = graph.getNode(e.getDest());

                if (neighbor.getTag() == 0) { //If neighbor is not a visited node
                    queue.add(neighbor); //Add it to the queue
                    neighbor.setTag(1); //Mark it as visited
                }
            }
        }

        if (allNodesWereVisited(graph))
            return true;

        return false;
    }

    // Returns the first node encountered in this graph's node collection.
    // O(1).
    private node_data getFirstNode(directed_weighted_graph graph) {
        for (node_data n : graph.getV())
            return n;
        return null;
    }


    @Override
    public String toString() {
        return graph.toString();
    }


    public List<node_data> connected_component(int src){
        List<node_data> connectedComList=new LinkedList<node_data>();
        if(graph==null || graph.getNode(src)==null){
            return connectedComList;
        }
        resetTags();
        directed_weighted_graph originalGraph=graph;
        isConnectedBFS(originalGraph.getNode(src));
        directed_weighted_graph transposeGraph=transpose(graph);
        isConnectedBFS(transposeGraph.getNode(src));
        for (node_data node:transposeGraph.getV()) {
            if(node.getTag()==2){
                connectedComList.add(node);
            }
        }
        Collections.reverse(connectedComList);
        return connectedComList;
    }

    public List<List<node_data>> connected_components() {
        List<List<node_data>> connectedComLists = new LinkedList<List<node_data>>();
        resetTags();
        List<Integer> visited = new LinkedList<Integer>();
        List<node_data> path=new LinkedList<node_data>();

        for (node_data node : graph.getV()){
            if(!visited.contains(node.getKey())){
                path=connected_component(node.getKey());
              for (node_data nodeP:path){
                  visited.add(nodeP.getKey());
              }
            }
            connectedComLists.add(path);
        }

        Collections.reverse(connectedComLists);

        return connectedComLists;
    }
}
