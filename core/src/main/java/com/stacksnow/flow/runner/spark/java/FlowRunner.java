/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package com.stacksnow.flow.runner.spark.java;

import com.stacksnow.flow.runner.spark.java.cli.ITask;
import com.stacksnow.flow.runner.spark.java.model.Process;
import com.stacksnow.flow.runner.spark.java.model.*;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FlowRunner {

    private String processesServiceUrl;
    private RestTemplate restTemplate;
    private String tasksServiceUrl;
    private String bucketName;

    private FlowRunner() {
        this.restTemplate = new RestTemplate();
    }

    private void startDAG(DAG dag) throws Exception {
        DirectedAcyclicGraph<Node, Edge> graph = new DirectedAcyclicGraph(Edge.class);
        dag.getNodes().forEach(node -> {
            if (null != node.getAttributes()) {
                graph.addVertex(node);
                node.getAttributes().setStatusId(2);
            }
        });
        dag.getEdges().forEach(edge -> {
            Node source = dag.getNodes().stream().filter(node -> node.getKey().equalsIgnoreCase(edge.getSource())).findFirst().orElseThrow(RuntimeException::new);
            Node target = dag.getNodes().stream().filter(node -> node.getKey().equalsIgnoreCase(edge.getTarget())).findFirst().orElseThrow(RuntimeException::new);
            graph.addEdge(source, target, new Edge(edge.getKey(), source.getKey(), target.getKey()));
        });
        TopologicalOrderIterator<Node, Edge> iterator = new TopologicalOrderIterator<>(graph);
        Task[] tasks = getTasksList();
        Map<String, Task> runnerEntries = new HashMap<>();
        Arrays.asList(tasks).forEach(task -> {
            runnerEntries.put(task.getTaskName(), task);
        });
        SparkFlowContext flowContext = new SparkFlowContext(bucketName);
        try {
            while (iterator.hasNext()) {
                Node node = iterator.next();
                String type = node.getAttributes().getType();
                System.out.println(type);
                if (null == runnerEntries.get(type)) {
                    throw new Exception("runner not found for " + node.getAttributes().getType());
                }
                Class<? extends ITask> taskClass = (Class<? extends ITask>) Class.forName(runnerEntries.get(type).getClassName());
                Set<Edge> incomingEdges = graph.incomingEdgesOf(node);
                incomingEdges.stream().map(Edge::getSource).forEach(System.out::println);
                String[] ins = incomingEdges.stream().map(Edge::getSource).toArray(String[]::new);
                flowContext.putResponse(node.getKey(), taskClass.newInstance().execute(flowContext, ins, node.getAttributes().getRequest()));
            }
        } finally {
            flowContext.getSparkSession().stop();
        }
    }

    private void start(String processId) throws Exception {
        Process process = getProcess(processId);
        try {
            DAG dag = process.getFlow().getDag();
            updateFlowStatus(process.getId(), "RUNNING");
            startDAG(dag);
            updateFlowStatus(process.getId(), "SUCCESS");
        } catch (Exception e) {
            e.printStackTrace();
            updateFlowStatus(process.getId(), "FAILED");
            throw e;
        }
    }

    private Task[] getTasksList() throws URISyntaxException {
        return restTemplate.exchange(RequestEntity.get(new URI(tasksServiceUrl)).headers(restHeaders()).build(), Task[].class).getBody();
    }

    private Process getProcess(String processId) throws URISyntaxException {
        return restTemplate.exchange(RequestEntity.get(new URI(processesServiceUrl + "/" + processId)).headers(restHeaders()).build(), Process.class).getBody();
    }

    private boolean updateFlowStatus(String flowId, String status) throws URISyntaxException {
        return restTemplate.exchange(RequestEntity.put(new URI(processesServiceUrl + "/status/" + flowId + "/" + status)).headers(restHeaders()).build(), String.class).getStatusCode().is2xxSuccessful();
    }

    private HttpHeaders restHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + offlineToken());
        return headers;
    }

    private String offlineToken() {
        return "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJHZmhORXJaaE9zdzNCeE9zTTZKRlJINW53Q25xTEo1Y19IVUR1QWlFdW5VIn0.eyJleHAiOjE2MTAzODI2NDQsImlhdCI6MTYxMDM0ODQ4NywiYXV0aF90aW1lIjoxNjEwMzQ2NjQ0LCJqdGkiOiJjZDE2NzkxZC1jNWIzLTQyZGQtYmRjZS04ZGVlMjg4NWZlZmIiLCJpc3MiOiJodHRwOi8vaG9zdC5kb2NrZXIuaW50ZXJuYWw6ODA4MC9hdXRoL3JlYWxtcy9tbG9wcyIsImF1ZCI6ImFjY291bnQiLCJzdWIiOiI3OGExMDU2MC1iOTcxLTRkMjEtOWViNS1iMmM1YjhmYTNjNTIiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJmbG93LWRlc2lnbmVyIiwibm9uY2UiOiIzNGFjN2M0Yi1iZDViLTRkMDEtOTlkMi0zYjcyZWVkZjc0YjYiLCJzZXNzaW9uX3N0YXRlIjoiMzdhZWZiNWYtYmE5Mi00ZDdhLWJlNTctZTAzYjQ4MTIyNjE5IiwiYWNyIjoiMCIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwOi8vbG9jYWxob3N0OjMwMDAiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIm9mZmxpbmVfYWNjZXNzIiwidW1hX2F1dGhvcml6YXRpb24iLCJST0xFX0ZMT1dfREVTSUdORVJfQURNSU4iXX0sInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJuYW1lIjoiRGl2eWEgS3VtYXJlc2giLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJkaXZ5YSIsImdpdmVuX25hbWUiOiJEaXZ5YSIsImZhbWlseV9uYW1lIjoiS3VtYXJlc2gifQ.bCoWvziex4VVnY9O602ARA7YK71obEmRhzM8z5D9JvZI9S8eggMyO-byWUehGQuHOyue5SdzhgcQaT2MuhPSJHlQQPewOGLy8N4NOHP2BUpKPPj3CtG6XrUSH6fwUxAaAiBPqV0RUGnaT1dimvrc2SIMw0w4AUVHhIwQx5De77HVH2V5IbF3jdOhKihZdQQcGe69fwa61P7WBD6ptogsropUojAy5WROrBjUflm8g6VEWei8zT_v73AJ-bI8vrTMj1PvQDZ5Nv-g520wLjG0zvQsL0Bl9hTisWmDujof0cpuMdmnmnWqtQJJ8RLuD1PaAs6gT40WGQKdnIALoLIs7w";
    }

    public static void main(String[] args) throws Exception {
        FlowRunner flowRunner = new FlowRunner();
        flowRunner.tasksServiceUrl = args[0];
        flowRunner.processesServiceUrl = args[1];
        flowRunner.bucketName = args[2];
        flowRunner.start(args[3]);
    }
}
