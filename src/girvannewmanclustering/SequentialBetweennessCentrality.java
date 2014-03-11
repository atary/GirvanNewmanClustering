/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package girvannewmanclustering;

import edu.uci.ics.jung.algorithms.scoring.EdgeScorer;
import edu.uci.ics.jung.algorithms.scoring.VertexScorer;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Stack;
import org.apache.commons.collections15.Transformer;
import org.apache.commons.collections15.functors.ConstantTransformer;

/**
 * This code is directly copied from JUNG library
 * @author Atakan
 */
public class SequentialBetweennessCentrality<V, E> implements VertexScorer<V, Double>, EdgeScorer<E, Double> {

    protected Graph<V, E> graph;
    protected Map<V, Double> vertex_scores;
    protected Map<E, Double> edge_scores;
    protected Map<V, SequentialBetweennessData> vertex_data;

    @SuppressWarnings("unchecked")
    public SequentialBetweennessCentrality(Graph<V, E> graph) {
        initialize(graph);
        computeBetweenness(new LinkedList<V>(), new ConstantTransformer(1));
    }

    protected void initialize(Graph<V, E> graph) {
        this.graph = graph;
        this.vertex_scores = new HashMap<V, Double>();
        this.edge_scores = new HashMap<E, Double>();
        this.vertex_data = new HashMap<V, SequentialBetweennessData>();

        for (V v : graph.getVertices()) {
            this.vertex_scores.put(v, 0.0);
        }

        for (E e : graph.getEdges()) {
            this.edge_scores.put(e, 0.0);
        }
    }

    protected void computeBetweenness(Queue<V> queue, Transformer<E, ? extends Number> edge_weights) {
        for (V v : graph.getVertices()) {
            // initialize the betweenness data for this new vertex
            for (V s : graph.getVertices()) {
                this.vertex_data.put(s, new SequentialBetweennessData());
            }

            vertex_data.get(v).numSPs = 1;
            vertex_data.get(v).distance = 0;

            Stack<V> stack = new Stack<V>();
            queue.offer(v);

            while (!queue.isEmpty()) {
                V w = queue.poll();
                stack.push(w);
                SequentialBetweennessData w_data = vertex_data.get(w);

                for (E e : graph.getOutEdges(w)) {
                    V x = graph.getOpposite(w, e);
                    if (x.equals(w)) {
                        continue;
                    }
                    double wx_weight = edge_weights.transform(e).doubleValue();

                    SequentialBetweennessData x_data = vertex_data.get(x);
                    double x_potential_dist = w_data.distance + wx_weight;

                    if (x_data.distance < 0) {
                        x_data.distance = x_potential_dist;
                        queue.offer(x);
                    }
                }
                for (E e : graph.getOutEdges(w)) {
                    V x = graph.getOpposite(w, e);
                    if (x.equals(w)) {
                        continue;
                    }
                    double e_weight = edge_weights.transform(e).doubleValue();
                    SequentialBetweennessData x_data = vertex_data.get(x);
                    double x_potential_dist = w_data.distance + e_weight;
                    if (x_data.distance == x_potential_dist) {
                        x_data.numSPs += w_data.numSPs;
                        x_data.incomingEdges.add(e);
                    }
                }
            }
            while (!stack.isEmpty()) {
                V x = stack.pop();

                for (E e : vertex_data.get(x).incomingEdges) {
                    V w = graph.getOpposite(x, e);
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

        if (graph instanceof UndirectedGraph) {
            for (V v : graph.getVertices()) {
                double v_score = vertex_scores.get(v).doubleValue();
                v_score /= 2.0;
                vertex_scores.put(v, v_score);
            }
            for (E e : graph.getEdges()) {
                double e_score = edge_scores.get(e).doubleValue();
                e_score /= 2.0;
                edge_scores.put(e, e_score);
            }
        }

        vertex_data.clear();
    }

    public Double getVertexScore(V v) {
        return vertex_scores.get(v);
    }

    public Double getEdgeScore(E e) {
        return edge_scores.get(e);
    }

    private class SequentialBetweennessData{ 

        double distance;
        double numSPs;
        List<E> incomingEdges;
        double dependency;

        SequentialBetweennessData() {
            distance = -1;
            numSPs = 0;
            incomingEdges = new ArrayList<E>();
            dependency = 0;
        }

        @Override
        public String toString() {
            return "[d:" + distance + ", sp:" + numSPs
                    + ", p:" + incomingEdges + ", d:" + dependency + "]\n";
        }
    }
}
