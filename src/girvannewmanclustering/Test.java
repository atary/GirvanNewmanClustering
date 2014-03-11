/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package girvannewmanclustering;

import edu.uci.ics.jung.algorithms.cluster.WeakComponentClusterer;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.Set;

/**
 *
 * @author Atakan
 */
public class Test {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        String file = "data/6K.txt";
        int numEdgesToRemove = 5;
        boolean parallel = true;

        UndirectedSparseGraph<Integer, Integer> g = new UndirectedSparseGraph();

        if (args.length > 0) {
            file = args[0];
            if (args.length > 1) {
                numEdgesToRemove = Integer.parseInt(args[1]);
                if (args.length > 2) {
                    parallel = Boolean.parseBoolean(args[2]);
                }
            }
        }

        try (Scanner bfr = new Scanner(new File(file))) {
            int edgeID = 0;
            String edgeStr;
            while (bfr.hasNext()) {
                edgeStr = bfr.nextLine();
                if (edgeStr.length() > 0) {
                    edgeID++;
                    String[] edgeArr = edgeStr.split(" ");
                    g.addEdge(edgeID, Integer.parseInt(edgeArr[0]), Integer.parseInt(edgeArr[1]));
                }
            }
        } catch (FileNotFoundException ex) {
            System.err.println(ex);
        }

        //Girvan-Newman Edge Betweenness Clustering starts here
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < numEdgesToRemove; i++) {
            if (parallel) {
                ParallelBetweennessCentrality bc = new ParallelBetweennessCentrality(g); //Calculate edge scores
                Integer to_remove = null;
                double score = 0;
                for (Integer e : g.getEdges()) { //Find the edge with the maximum edge betweenness score
                    if (bc.getEdgeScore(e) > score) {
                        to_remove = e;
                        score = bc.getEdgeScore(e);
                    }
                }
                g.removeEdge(to_remove); //Remove the edge with the maximum score
            } else {
                SequentialBetweennessCentrality bc = new SequentialBetweennessCentrality(g); //Calculate edge scores
                Integer to_remove = null;
                double score = 0;
                for (Integer e : g.getEdges()) { //Find the edge with the maximum edge betweenness score
                    if (bc.getEdgeScore(e) > score) {
                        to_remove = e;
                        score = bc.getEdgeScore(e);
                    }
                }
                g.removeEdge(to_remove); //Remove the edge with the maximum score
            }
        }

        WeakComponentClusterer<Integer, Integer> wcc = new WeakComponentClusterer<>();
        Set<Set<Integer>> clusters = wcc.transform(g);
        long endTime = System.currentTimeMillis();
        
        String pStr = "SEQUENTIALLY";
        if(parallel){
            pStr = "IN PARALLEL";
        }
        long timePassed = endTime - startTime;
        System.out.println("REMOVING " + numEdgesToRemove + " EDGES "+ pStr +" RESULTED IN FOLLOWING " + clusters.size() + " CLUSTERS IN " + timePassed + "MS.");
        /*int cID = 1;
        for (Set<Integer> cluster : clusters) {
            System.out.println("Cluster " + cID + ": " + cluster);
            cID++;
        }*/
    }
}
