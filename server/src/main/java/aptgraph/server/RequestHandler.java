/*
 * The MIT License
 *
 * Copyright 2016 Thibault Debatty & Thomas Gilon.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package aptgraph.server;

import aptgraph.core.Domain;
import aptgraph.core.Subnet;
import info.debatty.java.graphs.Graph;
import info.debatty.java.graphs.Neighbor;
import info.debatty.java.graphs.NeighborList;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.TreeMap;

/**
 * Request Handler definition file.
 *
 * @author Thibault Debatty
 * @author Thomas Gilon
 */
public class RequestHandler {

    private final Path input_dir;
    private final boolean study_out;

    private static final Logger LOGGER
            = Logger.getLogger(RequestHandler.class.getName());

    private final Memory m = new Memory();

    /**
     * Create new handler.
     *
     * @param input_dir Input directory
     * @param study_out Study output mode
     */
    public RequestHandler(final Path input_dir, final boolean study_out) {
        this.input_dir = input_dir;
        this.study_out = study_out;
    }

    /**
     * Give access to the internal memory of server.
     *
     * @return m Server memory object
     */
    public final Memory getMemory() {
        return this.m;
    }

    /**
     * A test json-rpc call, with no argument, that should return "hello".
     *
     * @return String : Test string "hello"
     */
    public final String test() {
        return "hello";
    }

    /**
     * A dummy method that returns some clusters of nodes and edges.
     *
     * @return List&lt;Graph&lt;Domain&gt;&gt; : Dummy list of domains
     */
    public final List<Graph<Domain>> dummy() {
        String user = "219.253.194.242";
        Graph<Domain> graph = FileManager.getUserGraphs(
                input_dir, user).getFirst();

        // Feature Fusion
        // URL/Domain clustering
        // Prune & clustering
        graph.prune(0.9);
        ArrayList<Graph<Domain>> clusters = graph.connectedComponents();

        // Filtering
        LinkedList<Graph<Domain>> filtered = new LinkedList<Graph<Domain>>();
        for (Graph<Domain> subgraph : clusters) {
            if (subgraph.size() < 10) {
                filtered.add(subgraph);
            }
        }
        System.out.println("Found " + filtered.size() + " clusters");
        return filtered;
    }

    /**
     * Give the list of users available in the log. This method modifies
     * all_users_list and all_subnets_list of the Memory object of the Server.
     *
     * @return ArrayList&lt;String&gt; : List of users
     */
    public final ArrayList<String> getUsers() {
        LOGGER.info("Reading list of subnets from disk...");
        try {
            File file = new File(input_dir.toString(), "subnets.ser");
            FileInputStream input_stream
                    = new FileInputStream(file.toString());
            ObjectInputStream input = new ObjectInputStream(
                    new BufferedInputStream(input_stream));
            m.setAllSubnetsList((ArrayList<String>) input.readObject());
            input.close();
        } catch (IOException ex) {
            System.err.println(ex);
        } catch (ClassNotFoundException ex) {
            System.err.println(ex);
        }

        LOGGER.info("Reading list of users from disk...");
        try {
            File file = new File(input_dir.toString(), "users.ser");
            FileInputStream input_stream
                    = new FileInputStream(file.toString());
            ObjectInputStream input = new ObjectInputStream(
                    new BufferedInputStream(input_stream));
            m.setAllUsersList((ArrayList<String>) input.readObject());
            input.close();
        } catch (IOException ex) {
            System.err.println(ex);
        } catch (ClassNotFoundException ex) {
            System.err.println(ex);
        }

        ArrayList<String> output = new ArrayList<String>();
        output.addAll(m.getAllSubnetsList());
        output.addAll(m.getAllUsersList());
        return output;
    }

    /**
     * Analyze the graph of a specific user. This method modifies all necessary
     * values in the Memory Object of the Server (possibly everything).
     *
     * @param user Targeted user or subnet
     * @param feature_weights Weights for feature fusion
     * @param feature_ordered_weights Ordered weights for feature fusion
     * @param prune_threshold_temp Pruning threshold given by user
     * @param max_cluster_size_temp Max cluster size given by user
     * @param prune_z_bool Indicator for prune_threshold_temp (True if z score)
     * @param cluster_z_bool Indicator for max_cluster_size_temp (True if z
     * score)
     * @param whitelist_bool Indicator for white listing (True if authorized)
     * @param white_ongo List of white listed domains on the go
     * @param number_requests Minimum number of requests sent by user for a
     * given domain
     * @param ranking_weights Ranking weights
     * @param apt_search Indicator for search of ".apt" domains (True if wanted)
     * @return Output : Output object of server needed for the web page
     */
    public final Output analyze(
            final String user,
            final double[] feature_weights,
            final double[] feature_ordered_weights,
            final double prune_threshold_temp,
            final double max_cluster_size_temp,
            final boolean prune_z_bool,
            final boolean cluster_z_bool,
            final boolean whitelist_bool,
            final String white_ongo,
            final double number_requests,
            final double[] ranking_weights,
            final boolean apt_search) {

        long start_time = System.currentTimeMillis();

        // Update users list and subnets list if needed
        if (m.getAllUsersList() == null || m.getAllSubnetsList() == null) {
            getUsers();
        }

        // Check input of the user
        if (!checkInputUser(user, feature_weights, feature_ordered_weights,
                prune_threshold_temp, max_cluster_size_temp,
                prune_z_bool, cluster_z_bool, number_requests,
                ranking_weights)) {
            return null;
        }
        boolean[] stages = checkInputChanges(user, feature_weights,
                feature_ordered_weights, prune_threshold_temp,
                max_cluster_size_temp, prune_z_bool,
                cluster_z_bool, whitelist_bool, white_ongo, number_requests,
                ranking_weights, apt_search);
        m.setCurrentK(FileManager.getK(input_dir));
        m.setUser(user);
        m.setFeatureWeights(feature_weights);
        m.setFeatureOrderedWeights(feature_ordered_weights);
        m.setPruneThresholdTemp(prune_threshold_temp);
        m.setMaxClusterSizeTemp(max_cluster_size_temp);
        m.setPruneZBool(prune_z_bool);
        m.setClusterZBool(cluster_z_bool);
        m.setWhitelistBool(whitelist_bool);
        m.setWhiteOngo(white_ongo);
        m.setNumberRequests(number_requests);
        m.setRankingWeights(ranking_weights);
        m.setAptSearch(apt_search);

        long estimated_time_1 = System.currentTimeMillis() - start_time;
        System.out.println("1: " + estimated_time_1 + " (User input checked)");

        // Create the list of users used to produce final graph
        if (stages[0]) {
            if (m.getUser().equals("0.0.0.0")) {
                m.setUsersList(m.getAllUsersList());
            } else if (Subnet.isSubnet(m.getUser())) {
                m.setUsersList(Subnet.getUsersInSubnet(
                        m.getUser(), m.getAllUsersList()));
            } else {
                m.setUsersList(new ArrayList<String>() {
                    {
                        add(m.getUser());
                    }
                });
            }

            // Load users graphs
            m.setAllDomains(new HashMap<String, Domain>(),
                    new HashMap<String, Domain>());
            loadUsersGraphs(start_time);

            long estimated_time_2 = System.currentTimeMillis() - start_time;
            System.out.println("2: " + estimated_time_2 + " (Data loaded)");

            // The json-rpc request was probably canceled by the user
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
        }

        m.setStdout("<pre>Number of users selected: "
                + m.getUsersList().size());
        m.concatStdout("<br>k-NN Graph: k: " + m.getCurrentK());
        m.concatStdout("<br>Total number of domains: "
                + m.getAllDomains().get("all").values().size());

        if (stages[1]) {
            // Compute each user graph
            LinkedList<Graph<Domain>> merged_graph_users
                    = computeUsersGraph();

            long estimated_time_3 = System.currentTimeMillis() - start_time;
            System.out.println("3: " + estimated_time_3
                    + " (Fusion of features done)");

            // Fusion of the users (Graph of Domains)
            double[] users_weights = new double[merged_graph_users.size()];
            for (int i = 0; i < merged_graph_users.size(); i++) {
                users_weights[i] = 1.0;
            }
            m.setMergedGraph(computeFusionGraphs(merged_graph_users, "",
                    users_weights, new double[]{0.0}, "all"));

            long estimated_time_4 = System.currentTimeMillis() - start_time;
            System.out.println("4: " + estimated_time_4
                    + " (Fusion of users done)");
        }
        if (stages[2]) {
            ArrayList<Double> similarities = listSimilarities();
            m.setMeanVarSimilarities(Utility.getMeanVariance(similarities));

            if (!study_out) {
                computeHistData(similarities, "prune");

                long estimated_time_5 = System.currentTimeMillis() - start_time;
                System.out.println("5: " + estimated_time_5
                        + " (Similarities hist. created)");
            }
        }

        // The json-rpc request was probably canceled by the user
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }

        if (stages[3]) {
            Graph<Domain> pruned_graph = new Graph<Domain>(m.getMergedGraph());
            // Prune
            pruned_graph = doPruning(pruned_graph, start_time);

            long estimated_time_6 = System.currentTimeMillis() - start_time;
            System.out.println("6: " + estimated_time_6 + " (Pruning done)");

            // The json-rpc request was probably canceled by the user
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }

            // Clustering
            m.setClusters(pruned_graph.connectedComponents());

            long estimated_time_7 = System.currentTimeMillis() - start_time;
            System.out.println("7: " + estimated_time_7
                    + " (Clustering done)");
        }

        // The json-rpc request was probably canceled by the user
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }

        if (m.getPruneZBool()) {
            m.concatStdout("<br>Prune Threshold : ");
            m.concatStdout("<br>    Mean = " + m.getMeanVarSimilarities()[0]);
            m.concatStdout("<br>    Variance = "
                    + m.getMeanVarSimilarities()[1]);
            m.concatStdout("<br>    Prune Threshold = "
                    + m.getPruneThreshold());
        }

        if (stages[4]) {
            ArrayList<Double> cluster_sizes = listClusterSizes(m.getClusters());
            m.setMeanVarClusters(Utility.getMeanVariance(cluster_sizes));

            if (!study_out) {
                computeHistData(cluster_sizes, "cluster");

                long estimated_time_8 = System.currentTimeMillis() - start_time;
                System.out.println("8: " + estimated_time_8
                        + " (Clusters hist. created)");
            }
        }

        // The json-rpc request was probably canceled by the user
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }

        if (stages[5]) {
            // Filtering
            doFiltering(start_time);

            long estimated_time_9 = System.currentTimeMillis() - start_time;
            System.out.println("9: " + estimated_time_9
                    + " (Filtering done)");
        }

        if (m.getClusterZBool()) {
            m.concatStdout("<br>Cluster Size : ");
            m.concatStdout("<br>    Mean = " + m.getMeanVarClusters()[0]);
            m.concatStdout("<br>    Variance = " + m.getMeanVarClusters()[1]);
            m.concatStdout("<br>    Max Cluster Size = "
                    + m.getMaxClusterSize());
        }

        if (stages[6]) {
            // White listing
            if (m.getWhitelistBool()) {
                whiteListing();
            } else {
                m.setFilteredWhiteListed(m.getFiltered());
            }

            long estimated_time_10 = System.currentTimeMillis() - start_time;
            System.out.println("10: " + estimated_time_10
                    + " (White listing done)");
        }

        if (m.getWhitelistBool()) {
            m.concatStdout("<br>Number of white listed domains: "
                    + m.getWhitelisted().size());
        }

        // The json-rpc request was probably canceled by the user
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }

        if (stages[7]) {
            // Ranking
            showRanking();

            long estimated_time_11 = System.currentTimeMillis() - start_time;
            System.out.println("11: " + estimated_time_11
                    + " (Ranking printed)");
        }

        m.concatStdout(m.getRankingPrint());
        m.concatStdout("<br>Found " + m.getFilteredWhiteListed().size()
                + " clusters</pre>");

        // Output
        return createOutput();
    }

    /**
     * Check input of user.
     *
     * @param user Targeted user or subnet
     * @param feature_weights Weights for feature fusion
     * @param feature_ordered_weights Ordered weights for feature fusion
     * @param prune_threshold_temp Pruning threshold given by user
     * @param max_cluster_size_temp Max cluster size given by user
     * @param prune_z_bool Indicator for prune_threshold_temp (True if z score)
     * @param cluster_z_bool Indicator for max_cluster_size_temp (True if z
     * score)
     * @param ranking_weights Ranking weights
     * @return boolean : True if no problem
     */
    private boolean checkInputUser(
            final String user,
            final double[] feature_weights,
            final double[] feature_ordered_weights,
            final double prune_threshold_temp,
            final double max_cluster_size_temp,
            final boolean prune_z_bool,
            final boolean cluster_z_bool,
            final double number_requests,
            final double[] ranking_weights) {
        // Check if user exists
        if (!m.getAllUsersList().contains(user)
                && !m.getAllSubnetsList().contains(user)) {
            return false;
        }

        // Verify the non negativity of weights and
        // the sum of the weights of features
        double sum_feature_weights = 0;
        for (double d : feature_weights) {
            sum_feature_weights += d;
            if (d < 0) {
                return false;
            }
        }
        double sum_ordered_weights = 0;
        for (double d : feature_ordered_weights) {
            sum_ordered_weights += d;
            if (d < 0) {
                return false;
            }
        }
        if (sum_feature_weights != 1 || sum_ordered_weights != 1) {
            return false;
        }

        // Verify input of user for pruning
        if (!prune_z_bool && prune_threshold_temp < 0) {
            return false;
        }
        // Verify input of user for clustering
        if (!cluster_z_bool && max_cluster_size_temp < 0) {
            return false;
        }

        // Verify input of user for min number of requests by user
        if (number_requests < 0) {
            return false;
        }

        // Verify the non negativity of weights and
        // the sum of the weights for ranking
        double sum_ranking_weights = 0;
        for (int i = 0; i < ranking_weights.length; i++) {
            sum_ranking_weights += ranking_weights[i];
            if (ranking_weights[i] < 0 && i != 2) {
                return false;
            }
        }

        if (Math.abs(sum_ranking_weights - 1) > 1E-10) {
            return false;
        }

        return true;
    }

    /**
     * Determine which stage of the algorithm as to be computed.
     *
     * @param user Targeted user or subnet
     * @param feature_weights Weights for feature fusion
     * @param feature_ordered_weights Ordered weights for feature fusion
     * @param prune_threshold_temp Pruning threshold given by user
     * @param max_cluster_size_temp Max cluster size given by user
     * @param prune_z_bool Indicator for prune_threshold_temp (True if z score)
     * @param cluster_z_bool Indicator for max_cluster_size_temp (True if z
     * score)
     * @param whitelist_bool Indicator for white listing (True if authorized)
     * @param white_ongo List of white listed domains on the go
     * @param number_requests Minimum number of requests sent by user for a
     * given domain
     * @param ranking_weights Ranking weights
     * @param apt_search Indicator for search of ".apt" domains (True if wanted)
     * @return boolean[]
     */
    private boolean[] checkInputChanges(
            final String user,
            final double[] feature_weights,
            final double[] feature_ordered_weights,
            final double prune_threshold_temp,
            final double max_cluster_size_temp,
            final boolean prune_z_bool,
            final boolean cluster_z_bool,
            final boolean whitelist_bool,
            final String white_ongo,
            final double number_requests,
            final double[] ranking_weights,
            final boolean apt_search) {

        // By default all stages have changed
        boolean[] stages = {true, true, true, true, true, true, true, true};

        if (m.getUser().equals(user)) {
            stages[0] = false;

            if (Arrays.equals(m.getFeatureWeights(), feature_weights)
                    && Arrays.equals(m.getFeatureOrderedWeights(),
                            feature_ordered_weights)) {
                stages[1] = false;

                if (m.getPruneZBool() == prune_z_bool) {
                    stages[2] = false;

                    if (m.getPruneThresholdTemp() == prune_threshold_temp) {
                        stages[3] = false;

                        if (m.getClusterZBool() == cluster_z_bool) {
                            stages[4] = false;

                            if (m.getMaxClusterSizeTemp()
                                    == max_cluster_size_temp) {
                                stages[5] = false;

                                if (m.getWhitelistBool() == whitelist_bool
                                        && m.getWhiteOngo() != null
                                        && m.getWhiteOngo()
                                                .equals(white_ongo)
                                        && m.getNumberRequests()
                                        == number_requests) {
                                    stages[6] = false;

                                    if (Arrays.equals(m.getRankingWeights(),
                                            ranking_weights)
                                            && m.getAptSearch() == apt_search) {
                                        stages[7] = false;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return stages;
    }

    /**
     * Load the graphs needed. This method modifies users_graphs and all_domains
     * of the Memory object of the Server.
     *
     * @param start_time Epoch of the start time of the computation
     */
    final void loadUsersGraphs(final long start_time) {
        m.setUsersGraphs(new HashMap<String, LinkedList<Graph<Domain>>>());
        for (String user_temp : m.getUsersList()) {
            LinkedList<Graph<Domain>> graphs_temp = FileManager.getUserGraphs(
                    input_dir, user_temp);

            // List all domains
            for (Domain dom : graphs_temp.getFirst().getNodes()) {
                m.getAllDomains().get("byUsers")
                        .put(user_temp + ":" + dom.getName(), dom);
                if (!m.getAllDomains().get("all").containsKey(dom.getName())) {
                    m.getAllDomains().get("all").put(dom.getName(), dom);
                } else if (!m.getAllDomains().get("all")
                        .get(dom.getName()).deepEquals(dom)) {
                    m.getAllDomains().get("all").put(dom.getName(),
                            m.getAllDomains().get("all")
                                    .get(dom.getName()).merge(dom));
                }
            }

            // Store user graph
            m.getUsersGraphs().put(user_temp, graphs_temp);
        }
    }

    /**
     * Fusion features of each user.
     *
     * @return LinkedList&lt;Graph&lt;Domain&gt;&gt; : List of the merged graph
     * of each user
     */
    final LinkedList<Graph<Domain>> computeUsersGraph() {
        LinkedList<Graph<Domain>> merged_graph_users
                = new LinkedList<Graph<Domain>>();
        for (String user_temp : m.getUsersList()) {
            // Load user graph
            LinkedList<Graph<Domain>> graphs_temp
                    = m.getUsersGraphs().get(user_temp);

            // Fusion of the features (Graph of Domains)
            merged_graph_users.add(computeFusionGraphs(graphs_temp,
                    user_temp, m.getFeatureWeights(),
                    m.getFeatureOrderedWeights(), "byUsers"));
        }

        return merged_graph_users;
    }

    /**
     * Compute the fusion of graphs.
     *
     * @param graphs List of the feature graphs of the user
     * @param user Targeted user
     * @param feature_weights Weights for feature fusion
     * @param feature_ordered_weights Ordered weights for feature fusion
     * @param mode Mode of operation ('byUsers' of fusion of features, 'all' if
     * fusion of users)
     * @return Graph&lt;Domain&gt; : Merged graph (Fusion of feature if
     * 'byUsers' mode and fusion of users if 'all' mode)
     */
    final Graph<Domain> computeFusionGraphs(
            final LinkedList<Graph<Domain>> graphs,
            final String user,
            final double[] weights,
            final double[] ordered_weights,
            final String mode) {
        // Weighted average using parameter weights
        Graph<Domain> merged_graph = new Graph<Domain>(Integer.MAX_VALUE);
        for (Entry<String, Domain> entry_1 : m.getAllDomains()
                .get(mode).entrySet()) {
            String key = entry_1.getKey();
            Domain node = entry_1.getValue();
            if ((mode.equals("byUsers") && key.startsWith(user))
                    || mode.equals("all")) {
                // The json-rpc request was probably canceled by the user
                if (Thread.currentThread().isInterrupted()) {
                    return null;
                }

                HashMap<Domain, Double> all_neighbors
                        = new HashMap<Domain, Double>();

                for (int i = 0; i < graphs.size(); i++) {
                    Graph<Domain> graph_temp = graphs.get(i);
                    String user_temp = "";
                    if (mode.equals("all")) {
                        user_temp = graph_temp.getNodes().iterator()
                                .next().element().getClient();
                    }
                    String key_user = key;
                    if (mode.equals("all") && !key_user.startsWith(user_temp)) {
                        key_user = user_temp + ":" + key;
                    }
                    if (graph_temp.containsKey(m.getAllDomains()
                            .get("byUsers").get(key_user))) {
                        NeighborList neighbors_temp = graph_temp
                                .getNeighbors(m.getAllDomains().get("byUsers")
                                        .get(key_user));

                        for (Neighbor<Domain> neighbor_temp : neighbors_temp) {
                            double new_similarity
                                    = weights[i] * neighbor_temp
                                            .getSimilarity();

                            if (mode.equals("byUsers") && all_neighbors
                                    .containsKey(neighbor_temp.getNode())) {
                                new_similarity += all_neighbors.get(
                                        neighbor_temp.getNode());
                            } else if (mode.equals("all")
                                    && all_neighbors.containsKey(
                                            m.getAllDomains().get("all")
                                                    .get(neighbor_temp.getNode()
                                                            .getName()))) {
                                new_similarity
                                        += all_neighbors.get(m.getAllDomains()
                                                .get("all").get(neighbor_temp
                                                .getNode().getName()));
                            }

                            if (new_similarity != 0) {
                                if (mode.equals("all")) {
                                    all_neighbors.put(
                                            m.getAllDomains().get(mode)
                                                    .get(neighbor_temp.getNode()
                                                            .getName()),
                                            new_similarity);
                                } else if (mode.equals("byUsers")) {
                                    all_neighbors.put(m.getAllDomains()
                                            .get(mode).get(user + ":"
                                            + neighbor_temp.getNode()
                                                   .getName()), new_similarity);
                                }
                            }

                        }
                    }
                }

                NeighborList nl = new NeighborList(Integer.MAX_VALUE);
                for (Entry<Domain, Double> entry_2 : all_neighbors.entrySet()) {
                    nl.add(new Neighbor(entry_2.getKey(), entry_2.getValue()));
                }
                merged_graph.put(node, nl);
            }
        }
        return merged_graph;
    }

    /**
     * Compute the list of all the similarities of domain graph.
     *
     * @return ArrayList&lt;Double&gt; : List of similarities
     */
    final ArrayList<Double> listSimilarities() {
        ArrayList<Double> similarities = new ArrayList<Double>();
        for (Domain dom : m.getMergedGraph().getNodes()) {
            NeighborList neighbors = m.getMergedGraph().getNeighbors(dom);
            for (Neighbor<Domain> neighbor : neighbors) {
                similarities.add(neighbor.getSimilarity());
            }
        }
        return similarities;
    }

    /**
     * Compute histogram data of a given list. This method modifies
     * hist_similarities or hist_cluster of the Memory object of the Server
     * (depending of the mode used).
     *
     * @param list List to study
     * @param mode Mode to use for the computation (= 'prune' OR 'cluster')
     * @return HashMap&lt;Double, Integer&gt; : Histogram data of desired mode
     */
    private void computeHistData(
            final ArrayList<Double> list,
            final String mode) {
        boolean z_bool;
        double mean;
        double variance;
        if (mode.equals("prune")) {
            z_bool = m.getPruneZBool();
            mean = m.getMeanVarSimilarities()[0];
            variance = m.getMeanVarSimilarities()[1];
        } else if (mode.equals("cluster")) {
            z_bool = m.getClusterZBool();
            mean = m.getMeanVarClusters()[0];
            variance = m.getMeanVarClusters()[1];
        } else {
            return;
        }
        ArrayList<Double> list_func = new ArrayList<Double>(list.size());
        // Transform list in z score if needed
        if (z_bool) {
            for (int i = 0; i < list.size(); i++) {
                list_func.add(i, Utility.getZ(mean, variance, list.get(i)));
            }
        } else {
            list_func = list;
        }
        ArrayList<Double> max_min = Utility.getMaxMin(list_func);
        double max = max_min.get(0);
        double min = max_min.get(1);
        double step;
        if (mode.equals("cluster")) {
            max = Math.round(max);
            min = Math.round(min);
            step = 1.0;
        } else {
            if (!z_bool) {
                max = Math.min(max, Utility.fromZ(mean, variance, 1.0));
                max = Math.max(1.0, max);
                step = 0.1;
            } else {
                max = Math.min(max, 1.0);
                max = Math.max(0.5, max);
                step = 0.01;
            }
        }

        HistData hist_data_temp = Utility.computeHistogram(
                list_func, min, max, step);
        HistData hist_data;
        if (hist_data_temp.size() > 3) {
            hist_data = Utility.cleanHistogram(hist_data_temp);
        } else {
            hist_data = hist_data_temp;
        }

        // there ara actually (bins + 2) bins (to include max in the histogram)
        if (mode.equals("prune")) {
            m.setHistDataSimilarities(hist_data);
        } else if (mode.equals("cluster")) {
            m.setHistDataClusters(hist_data);
        }
    }

    /**
     * Make the pruning on the graph and analyze the similarities. This method
     * modifies prune_threshold of the Memory object of the Server.
     *
     * @param graph Graph to prune
     * @param start_time Epoch of the start time of the computation
     * @return Graph&lt;Domain&gt; : Graph pruned
     */
    final Graph<Domain> doPruning(
            final Graph<Domain> graph,
            final long start_time) {
        if (m.getPruneZBool()) {
            m.setPruneThreshold(Utility.computePruneThreshold(
                    m.getMeanVarSimilarities()[0],
                    m.getMeanVarSimilarities()[1],
                    m.getPruneThresholdTemp()));
        } else {
            m.setPruneThreshold(m.getPruneThresholdTemp());
        }
        graph.prune(m.getPruneThreshold());
        return graph;
    }

    /**
     * Compute the list of the sizes of clusters.
     *
     * @param clusters List of clusters
     * @return ArrayList&lt;Double&gt; : List of clusters size
     */
    final ArrayList<Double> listClusterSizes(
            final ArrayList<Graph<Domain>> clusters) {
        ArrayList<Double> cluster_sizes = new ArrayList<Double>();
        for (Graph<Domain> subgraph : clusters) {
            cluster_sizes.add((double) subgraph.size());
        }
        return cluster_sizes;
    }

    /**
     * Make the filtering and analyze the cluster sizes. This method modifies
     * max_cluster_size and filtered of the Memory object of the Server.
     *
     * @param start_time Epoch of the start time of the computation
     */
    final void doFiltering(final long start_time) {
        LinkedList<Graph<Domain>> filtered = new LinkedList<Graph<Domain>>();
        if (m.getClusterZBool()) {
            m.setMaxClusterSize(Utility.computeClusterSize(
                    m.getMeanVarClusters()[0], m.getMeanVarClusters()[0],
                    m.getMaxClusterSizeTemp()));
        } else {
            m.setMaxClusterSize(m.getMaxClusterSizeTemp());
        }
        for (Graph<Domain> subgraph : m.getClusters()) {
            if (subgraph.size() <= m.getMaxClusterSize()) {
                filtered.add(subgraph);
            }
        }
        m.setFiltered(filtered);
    }

    /**
     * White List unwanted domains. This method modifies whitelisted and
     * filtered_white_listed of the Memory object of the Server.
     */
    final void whiteListing() {
        LinkedList<Graph<Domain>> filtered_whitelisted
                = new LinkedList<Graph<Domain>>();
        // Deep clone
        for (Graph<Domain> graph : m.getFiltered()) {
            filtered_whitelisted.add(new Graph<Domain>(graph));
        }

        List<String> whitelist = new ArrayList<String>();
        List<String> whitelist_ongo = new ArrayList<String>();
        LinkedList<Domain> whitelisted = new LinkedList<Domain>();
        try {
            whitelist = Files.readAllLines(m.getWhiteListPath(),
                    StandardCharsets.UTF_8);
            whitelist_ongo.addAll(Arrays.asList(m.getWhiteOngo().split("\n")));
        } catch (IOException ex) {
            Logger.getLogger(RequestHandler.class.getName())
                    .log(Level.SEVERE, null, ex);
        }

        Iterator<Graph<Domain>> iterator_1 = filtered_whitelisted.iterator();
        while (iterator_1.hasNext()) {
            Graph<Domain> domain_graph = iterator_1.next();
            Iterator<Domain> iterator_2 = domain_graph.getNodes().iterator();
            while (iterator_2.hasNext()) {
                Domain dom = iterator_2.next();
                if ((whitelist.contains(dom.getName())
                        || whitelist_ongo.contains(dom.getName()))
                        && !whitelisted.contains(dom)) {
                    whitelisted.add(dom);
                }
                for (String user : m.getUsersList()) {
                    if (m.getAllDomains().get("byUsers").get(user
                            + ":" + dom.getName()) != null) {
                        if (m.getAllDomains().get("byUsers").get(user
                                + ":" + dom.getName()).toArray().length
                                < m.getNumberRequests()
                                && !whitelisted.contains(dom)) {
                            whitelisted.add(dom);
                        }
                    }
                }
            }
            Utility.remove(domain_graph, whitelisted);
        }

        m.setWhitelisted(whitelisted);
        m.setFilteredWhiteListed(filtered_whitelisted);
    }

    /**
     * Print of the ranking list. This method modifies ranking_print and ranking
     * of the Memory object of the Server.
     */
    private void showRanking() {
        // Creation of a big graph with the result
        Graph<Domain> graph_all = new Graph<Domain>(Integer.MAX_VALUE);
        for (Graph<Domain> graph : m.getFilteredWhiteListed()) {
            for (Domain dom : graph.getNodes()) {
                if (!graph_all.containsKey(dom)) {
                    graph_all.put(dom, graph.getNeighbors(dom));
                } else {
                    NeighborList neighbors = graph_all.getNeighbors(dom);
                    neighbors.addAll(graph.getNeighbors(dom));
                    graph_all.put(dom, neighbors);
                }
            }
        }
        // List all the remaining domains
        List<Domain> list_domain = new LinkedList<Domain>();
        for (Domain dom : graph_all.getNodes()) {
            list_domain.add(dom);
        }
        m.setRankingPrint("<br>Number of domains shown: " + list_domain.size());
        // Number of children
        HashMap<Domain, Double> index_children
                = new HashMap<Domain, Double>();
        // Number of parents
        HashMap<Domain, Double> index_parents
                = new HashMap<Domain, Double>();
        // Number of requests index
        HashMap<Domain, Double> index_requests
                = new HashMap<Domain, Double>();

        // Number of children & Number of requests
        for (Domain dom : graph_all.getNodes()) {
            index_children.put(dom, 0.0);
            index_parents.put(dom, 0.0);
            index_requests.put(dom, (double) dom.size());
        }
        // Number of parents
        for (Domain parent : graph_all.getNodes()) {
            for (Neighbor<Domain> child : graph_all.getNeighbors(parent)) {
                index_children.put(parent,
                        index_children.get(parent) + child.getSimilarity());
                index_parents.put(child.getNode(), index_parents.get(
                        child.getNode()) + child.getSimilarity());
            }
        }
        // Fusion of indexes
        HashMap<Domain, Double> index = new HashMap<Domain, Double>();
        for (Domain dom : graph_all.getNodes()) {
            index.put(dom,
                    m.getRankingWeights()[0] * index_parents.get(dom)
                    + m.getRankingWeights()[1] * index_children.get(dom)
                    + m.getRankingWeights()[2] * index_requests.get(dom));
        }
        //Sort
        ArrayList<Domain> sorted = Utility.sortByIndex(list_domain, index);
        // Print out
        if (m.getAptSearch()) {
            double top = 0.0;
            double rank = Double.MAX_VALUE;
            boolean founded = false;
            LinkedList<Domain> apt_domains = new LinkedList<Domain>();
            for (Domain dom : sorted) {
                if (dom.getName().endsWith(".apt")) {
                    rank = index.get(dom);
                    top++;
                    founded = true;
                    apt_domains.add(dom);
                }
                if (!dom.getName().endsWith(".apt")
                        && index.get(dom) <= rank) {
                    top++;
                }
            }
            if (founded) {
                m.concatRankingPrint("<br>TOP for first APT: "
                        + Math.round(top / m.getAllDomains()
                                .get("all").values().size() * 100 * 100)
                        / 100.0 + "%");
                m.concatRankingPrint("<br>Number of APT domains : "
                        + apt_domains.size());
                m.concatRankingPrint("<br>APT domains : ");
                for (Domain apt_dom : apt_domains) {
                    m.concatRankingPrint("<br>    ("
                            + Math.round(index.get(apt_dom) * 100) / 100.0
                            + ") " + apt_dom.getName());
                }
            } else {
                m.concatRankingPrint("<br>TOP for APT: NOT FOUND");
            }
        }
        m.concatRankingPrint("<br>Ranking:");
        m.setRanking(new TreeMap<Double, LinkedList<String>>());
        for (Domain dom : sorted) {
            m.concatRankingPrint("<br>    ("
                    + Math.round(index.get(dom) * 100) / 100.0 + ") "
                    + dom.getName());
            if (!m.getRanking().keySet().contains(index.get(dom))) {
                LinkedList<String> list = new LinkedList<String>();
                list.add(dom.getName());
                m.getRanking().put(index.get(dom), list);
            } else {
                m.getRanking().get(index.get(dom)).add(dom.getName());
            }
        }
    }

    /**
     * Create the output variable.
     *
     * @return Output : Output object of server needed for the web page
     */
    private Output createOutput() {
        Output output = new Output();
        output.setStdout(m.getStdout());
        if (!study_out) {
            output.setFilteredWhiteListed(m.getFilteredWhiteListed());
            output.setHistDataSimilarities(m.getHistDataSimilarities());
            output.setHistDataClusters(m.getHistDataClusters());
        }
        if (study_out) {
            output.setRanking(m.getRanking());
        }
        return output;
    }

    /**
     * Give the list of requests of a specific domain. This method is used by
     * the web page to show log lines of a specific domain.
     *
     * @param domain Domain name
     * @return Object[] : Array of requests of a specific domain
     */
    public final Object[] getRequests(final String domain) {
        LOGGER.log(Level.INFO, "Sending requests of the domain {0}", domain);
        return m.getAllDomains("all").get(domain).toArray();
    }
}
