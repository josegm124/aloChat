package com.alochat.tools.msk;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;

public final class MskTopicBootstrap {

    private static final List<String> DEFAULT_TOPICS = List.of(
            "messages.ingress.normalized",
            "messages.processing.ai",
            "messages.outbound.dispatch",
            "messages.retry.short",
            "messages.retry.long",
            "messages.dlq"
    );

    private MskTopicBootstrap() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> arguments = parseArguments(args);
        String bootstrapServers = required(arguments, "bootstrap-servers");
        String region = arguments.getOrDefault("region", "us-east-1");
        short replicationFactor = Short.parseShort(arguments.getOrDefault("replication-factor", "1"));
        int partitions = Integer.parseInt(arguments.getOrDefault("partitions", "1"));
        Set<String> topics = parseTopics(arguments.get("topics"));

        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put("security.protocol", "SASL_SSL");
        properties.put("sasl.mechanism", "AWS_MSK_IAM");
        properties.put("sasl.jaas.config", "software.amazon.msk.auth.iam.IAMLoginModule required;");
        properties.put("sasl.client.callback.handler.class", "software.amazon.msk.auth.iam.IAMClientCallbackHandler");
        properties.put("client.id", "alochat-msk-admin");
        properties.put("request.timeout.ms", String.valueOf(Duration.ofSeconds(30).toMillis()));
        properties.put("default.api.timeout.ms", String.valueOf(Duration.ofSeconds(30).toMillis()));
        properties.put("aws.region", region);

        try (AdminClient adminClient = AdminClient.create(properties)) {
            ListTopicsResult listTopicsResult = adminClient.listTopics();
            Set<String> existingTopics = listTopicsResult.names().get();
            List<NewTopic> newTopics = topics.stream()
                    .filter(topic -> !existingTopics.contains(topic))
                    .map(topic -> new NewTopic(topic, partitions, replicationFactor))
                    .toList();

            if (newTopics.isEmpty()) {
                System.out.println("All requested topics already exist.");
                return;
            }

            CreateTopicsResult createTopicsResult = adminClient.createTopics(newTopics);
            try {
                createTopicsResult.all().get();
            } catch (ExecutionException exception) {
                throw new IllegalStateException("Unable to create topics: " + newTopics, exception.getCause());
            }

            System.out.println("Created topics: "
                    + newTopics.stream().map(NewTopic::name).collect(Collectors.joining(", ")));
        }
    }

    private static Set<String> parseTopics(String rawTopics) {
        if (rawTopics == null || rawTopics.isBlank()) {
            return new LinkedHashSet<>(DEFAULT_TOPICS);
        }

        Set<String> topics = new LinkedHashSet<>();
        for (String topic : rawTopics.split(",")) {
            String normalized = topic.trim();
            if (!normalized.isEmpty()) {
                topics.add(normalized);
            }
        }
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("At least one topic must be provided.");
        }
        return topics;
    }

    private static Map<String, String> parseArguments(String[] args) {
        Map<String, String> parsed = new HashMap<>();
        List<String> dangling = new ArrayList<>();

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (!argument.startsWith("--")) {
                dangling.add(argument);
                continue;
            }

            String key = argument.substring(2);
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for argument --" + key);
            }
            parsed.put(key, args[++index]);
        }

        if (!dangling.isEmpty()) {
            throw new IllegalArgumentException("Unexpected positional arguments: " + dangling);
        }

        return parsed;
    }

    private static String required(Map<String, String> arguments, String key) {
        String value = arguments.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required argument --" + key);
        }
        return value;
    }
}
