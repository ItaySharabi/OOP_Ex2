package gameClient;

import Server.Game_Server_Ex2;
import api.game_service;
import api.*;
import com.google.gson.*;
import gameClient.util.Point3D;

import java.util.*;

public class Ex2 implements Runnable {

    private static List<CL_Pokemon> _pokemons;
    private static List<CL_Agent> _agents;
    private static game_service _game;
    private static Arena _ar;
    private static MyFrame _win;
    private static HashMap<Integer, HashMap<Integer, List<node_data>>> allRoutes;
    private static List<node_data> agentCurrentPath;
    private static dw_graph_algorithms graphAlgo;
    private static directed_weighted_graph graph;


    public static void main(String[] args) {

        //TODO: Add a countdown method to calc time/moves ratio. Should not go over (10 moves / 1 sec).
        //TODO: Add a test method for the time/moves ratio.
        //TODO: Window thread - Check how to optimize thread sleep functionality.
        //TODO: Improve myFrame class.
        //TODO: Improve the decision of how to set curr fruit for an agent, if there are multiple agents
        // In other words--> we need to set the closest agent to the ideal edge
        // (moveAgent() method).

        int level = 11;
        _game = Game_Server_Ex2.getServer(level);
        init();
        Thread client = new Thread(new Ex2());
        client.start();
    }

    private static void init() {

        _ar = new Arena(); //Init a new Arena.
        graphAlgo = new DWGraph_Algo(loadGraph(_game.getGraph())); //Init graph algo class with the game graph.
        graph = graphAlgo.getGraph(); //Get a reference to the game graph.

        _pokemons = Arena.json2Pokemons(_game.getPokemons()); //Create a Pokemon list from a Json.


        for (int i = 0; i < _pokemons.size(); i++) //Iterate over all pokemons
            Arena.updateEdge(_pokemons.get(i), graph); //And match them with the right edge on the graph

        _ar.setGraph(graph);
        _ar.setPokemons(_pokemons); //Set the arena with the generated info.

        initiallySetGameAgents(); //TODO: Done so far -- Only minor fixes.
        _ar.setAgents(_agents);

        _win = new MyFrame("test Ex2");
        _win.setSize(1000, 700);
        _win.update(_ar);
        _win.show();
        System.out.println("poke \n" + _pokemons);
        System.out.println("agents \n" + _agents);
        allRoutes = new HashMap<Integer, HashMap<Integer, List<node_data>>>();
        calcAllPaths(graphAlgo);
    }

    @Override
    public void run() {
        _game.startGame();
        _win.setTitle("Ex2 - OOP: (NONE trivial Solution) " + _game.toString());
        long dt = 100; // Created for thread's sleep

        while (_game.isRunning()) {
            moveAgents(_game, _ar.getGraph());
            try {
                _win.repaint();
                Thread.sleep(dt);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        String res = _game.toString();

        System.out.println(res);
        System.exit(0);
    }

    /**
     * Moves each of the agents along the edge,
     * in case the agent is on a node the next destination (next edge) is chosen (randomly).
     *
     * @param game
     * @param graph
     * @param
     */
    private static void moveAgents(game_service game, directed_weighted_graph graph) {
        String lg = game.move(); // Need to use at least 10 times in 1 sec according to boaz instruction
        _win.setTitle("Ex2 - OOP: (NONE trivial Solution) " + _game.toString());
        _agents = Arena.getAgents(lg, graph); //receive the last update for agents locations after game.move().
        _ar.setAgents(_agents);
        String fs = game.getPokemons();
        _pokemons = Arena.json2Pokemons(fs);
        boolean isStuck = false;

        for (CL_Pokemon poke : _pokemons)
            Arena.updateEdge(poke, graph);
        _ar.setPokemons(_pokemons);

        agentCurrentPath = new LinkedList<>();
        CL_Agent ag;
        int dest;
        for (int i = 0; i < _ar.getAgents().size(); i++) {
            ag = _ar.getAgents().get(i);

            if (getBestPokemon(ag)) {// Match given agent with the best pokemon on the ideal edge.
                agentCurrentPath = getShortestPathTo(ag, ag.get_curr_fruit().get_edge().getSrc());
                trackPokemonsOnList(agentCurrentPath);
            } else //agent has nowhere to go and needs to go the lowest amount of moves.
                isStuck = true;

            int id = ag.getID();
            if(!isStuck) {
                if (agentCurrentPath.size() > 1)
                    dest = agentCurrentPath.get(1).getKey(); // Next dest will always be at index 1 on the list.
                else dest = ag.get_curr_fruit().get_edge().getDest(); // Catch the pokemon
            }
            else dest = getMinimalNode(ag.getCurrNode());

            game.chooseNextEdge(id, dest);
            double v = ag.getValue();
            System.out.println("Agent: " + id + ", val: " + v + "   turned to node: " + dest);
        }
    }

    public static directed_weighted_graph loadGraph(String json) {
        directed_weighted_graph newGraph = new DWGraph_DS();
        JsonObject graph = new JsonParser().parse(json).getAsJsonObject();
        JsonArray edges = graph.getAsJsonArray("Edges"); //Get The "Edges" member from the Json Value.
        JsonArray nodes = graph.getAsJsonArray("Nodes"); //Get The "Edges" member from the Json Value.

        for (JsonElement node : nodes) {

            int key = ((JsonObject) node).get("id").getAsInt();
            double x, y, z;
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
        return newGraph;
    }


    public static List<node_data> getShortestPathTo(CL_Agent agent, int pokeDest) {
        return allRoutes.get(agent.getSrcNode()).get(pokeDest);
    }

    public static void initiallySetGameAgents() {

        //Receive info from the game server for agent capacity.
        JsonElement gameElement = JsonParser.parseString(_game.toString());
        JsonObject gameServerObjects = gameElement.getAsJsonObject();
        JsonElement gameServerElements = gameServerObjects.get("GameServer");
        JsonObject gameServerObject = gameServerElements.getAsJsonObject();
        int agentCapacity = gameServerObject.get("agents").getAsInt();
        int[] occupiedNodes = new int[agentCapacity];

        CL_Pokemon[] pokemon = new CL_Pokemon[agentCapacity]; //
        int initialNode;
        for (int i = 0; i < agentCapacity; i++) {
            pokemon[i] = getInitialMaxRatio();
            if (pokemon[i] == null) //Means all pokemons are tracked and the next agent will "wonder".
                initialNode = bestInitialNode(occupiedNodes); //Find the best arbitrary node to send the agent to.
            else
                initialNode = pokemon[i].get_edge().getSrc();

            occupiedNodes[i] = initialNode;
            _game.addAgent(initialNode);
//            pokemon[i].setisTracked(true);
        }
        _agents = Arena.getAgents(_game.getAgents(), graphAlgo.getGraph());
        for (int i = 0; i < agentCapacity; i++) {
            _agents.get(i).set_curr_fruit(pokemon[i]);
        }
    }

    /**
     * @return
     */
    private static int bestInitialNode(int[] nodesNotToUse) {
        node_data occupied;
        boolean isAvailable = false;
        int ans = 0;
        for (node_data node : graph.getV()) {

            for (int i = 0; i < nodesNotToUse.length; i++) {
                occupied = graph.getNode(nodesNotToUse[i]);
                if (!node.equals(occupied)) {
                    isAvailable = true;
                    ans = node.getKey();
                }
            }
            if (isAvailable)
                return ans;
        }

        /*
        If reached this return statement, all nodes on the graph are currently occupied,
        and cannot find any free node, so return just any node.
         */
        return graph.getNode(nodesNotToUse[0]).getKey();
    }

    private static int getMinimalNode(node_data from) {
        double min = Double.MAX_VALUE;
        int ans = 0;
        for (edge_data e : graph.getE(from.getKey())) {
            if (e.getWeight() < min) {
                ans = e.getSrc();
                min = e.getWeight();
            }
        }
        return ans;
    }

    /**
     * This method returns the pokemon with the max value that is not tracked by another
     * agent.
     *
     * @return the pokemon with the lowest ratio (Best decision).
     */
    public static CL_Pokemon getInitialMaxRatio() { // Created for init agents
        CL_Pokemon poke = null;
        double minRatio = Double.MAX_VALUE, weight, value, ratio;

        for (CL_Pokemon pokei : _pokemons) {
            if (!pokei.getisTracked()) {

                weight = pokei.get_edge().getWeight();
                value = sumEdgeValue(pokei.get_edge());
                ratio = weight / value;

                if (minRatio > ratio) {
                    minRatio = ratio;
                    poke = pokei;
                }
            }
        }
        if (poke == null) return null;
        trackPokemonsOnEdge(poke.get_edge()); //Marks all pokemons on the edge as tracked.
        return poke;
    }

    public static boolean getBestPokemon(CL_Agent ag) {
        double dist, minRatio = Double.MAX_VALUE; //minRatio gives the best Pokemon.
        double value, minpath;
        boolean isMatched = true;

        CL_Pokemon pokemon = null;
        List<node_data> path; //Execute a shortestPath Algo from src to dest.
        for (CL_Pokemon poke : _pokemons) {
            if (!poke.getisTracked()) {

                path = allRoutes.get(ag.getSrcNode()).get(poke.get_edge().getSrc());//Execute a shortestPath Algo from src to dest.
                if (path != null) {
                    minpath = pathDist(path) + poke.get_edge().getWeight();// dist between curr ag to curr poke
                } else minpath = poke.get_edge().getWeight();

                value = sumEdgeValue(poke.get_edge());
                dist = minpath / value; // Pokemon value-dist ratio.

                if (minRatio > dist) {
                    pokemon = poke;
                    minRatio = dist;
                }
            }
        }

        ag.set_curr_fruit(pokemon);
        if (ag.get_curr_fruit() == null) isMatched = false;
//        pokemon.setisTracked(true);
        return isMatched;
    }

    public static double pathDist(List<node_data> path) {
        double weight = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            node_data node1 = path.get(i);
            node_data node2 = path.get(i + 1);
            weight += graph.getEdge(node1.getKey(), node2.getKey()).getWeight();

        }
        return weight;
    }

    /**
     * this fucn sets for the threads how much to sleep
     */
    private synchronized int timeSleep() {
        int sleep = 40; //1
        for (CL_Agent agent : _agents) {
            if (_pokemons != null) {
                for (CL_Pokemon pokemon : _pokemons) {
                    if (pokemon.get_edge() != null) {
                        if (agent.getSrcNode() == pokemon.get_edge().getSrc() && agent.getNextNode() == pokemon.get_edge().getDest()) {
                            sleep = 1; //2
                        } else
                            sleep = 50;//3
                    }
                }
            }
        }
        return sleep;
    }

    /**
     * This method sums all pokemon values that are associated with the given edge.
     *
     * @param edge
     * @return
     */
    private static double sumEdgeValue(edge_data edge) {
        double sum = 0;
        for (CL_Pokemon poke : _pokemons) {
            if (poke.get_edge().equals(edge)) sum += poke.getValue();
        }
        return sum;
    }

    /**
     * This method marks all pokemons on a given path as tracked pokemons.
     *
     * @param path
     */
    public static void trackPokemonsOnList(List<node_data> path) {

        edge_data edge;
        for (int i = 0; i < path.size() - 1; i++) {
            node_data node1 = path.get(i);
            node_data node2 = path.get(i + 1);
            edge = graph.getEdge(node1.getKey(), node2.getKey());
            trackPokemonsOnEdge(edge);
        }
    }

    /**
     * This method marks all Pokemons on the given edge 'e' as tracked.
     *
     * @param e
     */
    public static void trackPokemonsOnEdge(edge_data e) {
        for (CL_Pokemon poke : _pokemons) {
            if (!poke.getisTracked())
                if (poke.get_edge().equals(e)) poke.setisTracked(true);
        }
    }

    /**
     * This method computes and stores all shortest paths from
     * all nodes on the graph to all others.
     * Stores the data in a HashMap<K,V> allRoutes.
     *
     * @param graphAlgo
     */
    public static void calcAllPaths(dw_graph_algorithms graphAlgo) { // need to change a little
        directed_weighted_graph graph = graphAlgo.getGraph();
        //added empty map to add values after
        Iterator<node_data> itr = graph.getV().iterator();

        while (itr.hasNext()) {
            HashMap<Integer, List<node_data>> pathsMap = new HashMap<Integer, List<node_data>>(); //Init new HashMap for a node
            allRoutes.put(itr.next().getKey(), pathsMap); //Put (node, pathsMap).
        }

        //Iterate over all graph nodes
        for (node_data node : graph.getV()) {
            int src = node.getKey();

            for (node_data destNode : graph.getV()) {
                int dest = destNode.getKey();
                List<node_data> list = graphAlgo.shortestPath(src, dest);
                allRoutes.get(src).put(dest, list);
            }
        }
    }


}
