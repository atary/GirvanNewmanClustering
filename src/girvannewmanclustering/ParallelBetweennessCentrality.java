/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package girvannewmanclustering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Stack;

import edu.uci.ics.jung.algorithms.scoring.EdgeScorer;
import edu.uci.ics.jung.algorithms.scoring.VertexScorer;

import org.apache.commons.collections15.Transformer;
import org.apache.commons.collections15.functors.ConstantTransformer;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedGraph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.text.html.HTMLDocument;

/**
 *
 * @author Atakan
 */
public class ParallelBetweennessCentrality implements VertexScorer<Integer, Double>, EdgeScorer<Integer, Double> {

    protected Graph<Integer, Integer> graph;
    protected Map<Integer, Double> vertex_scores;
    protected Map<Integer, Double> edge_scores;
    protected Map<Integer, ParallelBetweennessData> vertex_data;

    public ParallelBetweennessCentrality(Graph<Integer, Integer> graph) {
        initialize(graph);
        computeBetweenness(new LinkedList<Integer>(), new ConstantTransformer(1));
    }

    protected void initialize(Graph<Integer, Integer> graph) {
        this.graph = graph;
        this.vertex_scores = new HashMap<Integer, Double>();
        this.edge_scores = new HashMap<Integer, Double>();
        this.vertex_data = new HashMap<Integer, ParallelBetweennessData>();

        for (Integer v : graph.getVertices()) {
            this.vertex_scores.put(v, 0.0);
        }

        for (Integer e : graph.getEdges()) {
            this.edge_scores.put(e, 0.0);
        }
    }

    public class RunnableComputation implements Runnable {

        private final VertexCounter counter;
        Queue<Integer> queue;
        HashMap<Integer, ParallelBetweennessData> vertex_data;
        Graph<Integer, Integer> graph;
        Transformer<Integer, ? extends Number> edge_weights;

        public RunnableComputation(VertexCounter counter, Queue<Integer> queue, Map<Integer, ParallelBetweennessData> vertex_data, Graph<Integer, Integer> graph, Transformer<Integer, ? extends Number> edge_weights) {
            this.counter = counter;
            
            this.queue = new LinkedList<Integer>();
            for(int i : queue){
                this.queue.add(i);
            }
            this.vertex_data = new HashMap<Integer, ParallelBetweennessData>();
            for(int i : vertex_data.keySet()){
                this.vertex_data.put(i,vertex_data.get(i));
            }
            this.graph = new UndirectedSparseGraph<Integer, Integer>();
            for(Integer i : graph.getEdges()){
                this.graph.addEdge(i, graph.getEndpoints(i).getFirst(), graph.getEndpoints(i).getSecond());
            }
            this.edge_weights = edge_weights;
        }

        @Override
        public void run() {
            while (true) {
                //System.out.println(Thread.currentThread().getName());
                int v;
                synchronized (counter) {
                    if (counter.isComplete()) {
                        break;
                    }
                    v = counter.getNextVertex();
                }
                for (Integer s : graph.getVertices()) {
                    vertex_data.put(s, new ParallelBetweennessData());
                }

                vertex_data.get(v).numSPs = 1;
                vertex_data.get(v).distance = 0;

                Stack<Integer> stack = new Stack<Integer>();
                queue.offer(v);

                while (!queue.isEmpty()) {
                    Integer w = queue.poll();
                    stack.push(w);
                    ParallelBetweennessData w_data = vertex_data.get(w);
                    if(graph.getOutEdges(w)==null) continue;
                    for (Integer e : graph.getOutEdges(w)) {
                        Integer x = graph.getOpposite(w, e);
                        if (x.equals(w)) {
                            continue;
                        }
                        double wx_weight = edge_weights.transform(e).doubleValue();

                        ParallelBetweennessData x_data = vertex_data.get(x);
                        double x_potential_dist = w_data.distance + wx_weight;

                        if (x_data.distance < 0) {
                            x_data.distance = x_potential_dist;
                            queue.offer(x);
                        }
                    }
                    for (Integer e : graph.getOutEdges(w)) {
                        Integer x = graph.getOpposite(w, e);
                        if (x.equals(w)) {
                            continue;
                        }
                        double e_weight = edge_weights.transform(e).doubleValue();
                        ParallelBetweennessData x_data = vertex_data.get(x);
                        double x_potential_dist = w_data.distance + e_weight;
                        if (x_data.distance == x_potential_dist) {
                            x_data.numSPs += w_data.numSPs;
                            x_data.incomingEdges.add(e);
                        }
                    }
                }
                while (!stack.isEmpty()) {
                    Integer x = stack.pop();
                    if(vertex_data.get(x)==null) continue;
                    for (Integer e : vertex_data.get(x).incomingEdges) {
                        Integer w = graph.getOpposite(x, e);
                        double partialDependency =
                                vertex_data.get(w).numSPs / vertex_data.get(x).numSPs
                                * (1.0 + vertex_data.get(x).dependency);
                        vertex_data.get(w).dependency += partialDependency;
                        double e_score = edge_scores.get(e).doubleValue();
                        edge_scores.put(e, e_score + partialDependency);
                    }
                    if (!x.equals(v)) {
                        double x_score = vertex_scores.get(x).doubleValue();
                        x_score += vertex_data.get(x).dependency;
                        vertex_scores.put(x, x_score);
                    }
                }
            }
        }
    }

    public static class VertexCounter {

        private ConcurrentLinkedQueue<Integer> vertices;

        public VertexCounter(Graph<Integer, Integer> graph) {
            vertices = new ConcurrentLinkedQueue<>();
            for(int i : graph.getVertices()){
                vertices.add(i);
            }
        }

        public synchronized int getNextVertex() {
            if (isComplete()) {
                throw new IllegalStateException("Already fully processed");
            }
            return vertices.poll();
        }

        public synchronized boolean isComplete() {
            return vertices.isEmpty();
        }
    }

    protected void computeBetweenness(Queue<Integer> queue, Transformer<Integer, ? extends Number> edge_weights) {

        VertexCounter counter = new VertexCounter(graph);
        Runnable work = new RunnableComputation(counter, queue, vertex_data, graph, edge_weights);
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(work, "Thread " + i);
        }
        for (int i = 0; i < threads.length; i++) {
            Thread t = threads[i];
            t.start();
        }
        for (int i = 0; i < threads.length; i++) {
            Thread t = threads[i];
            try {
                t.join();
            } catch (InterruptedException ex) {
                System.err.println(ex);
            }
        }

        if (graph instanceof UndirectedGraph) {
            for (Integer v : graph.getVertices()) {
                double v_score = vertex_scores.get(v).doubleValue();
                v_score /= 2.0;
                vertex_scores.put(v, v_score);
            }
            for (Integer e : graph.getEdges()) {
                double e_score = edge_scores.get(e).doubleValue();
                e_score /= 2.0;
                edge_scores.put(e, e_score);
            }
        }

        vertex_data.clear();
    }

    public Double getVertexScore(Integer v) {
        return vertex_scores.get(v);
    }

    public Double getEdgeScore(Integer e) {
        return edge_scores.get(e);
    }

    private class ParallelBetweennessData {

        double distance;
        double numSPs;
        List<Integer> incomingEdges;
        double dependency;

        ParallelBetweennessData() {
            distance = -1;
            numSPs = 0;
            incomingEdges = new ArrayList<Integer>();
            dependency = 0;
        }

        @Override
        public String toString() {
            return "[d:" + distance + ", sp:" + numSPs
                    + ", p:" + incomingEdges + ", d:" + dependency + "]\n";
        }
    }
}
