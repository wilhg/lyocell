package com.wilhg.lyocell.modules;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import io.grpc.*;
import io.grpc.stub.ClientCalls;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class GrpcModule implements LyocellModule {
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private Context context;

    @Override
    public String getName() {
        return "lyocell/net/grpc";
    }

    @Override
    public String getJsSource() {
        return """
            const Grpc = globalThis.LyocellGrpc;
            export class Client {
                constructor() {
                    this.id = Grpc.createClient();
                }
                load(paths, importDir) {
                    return Grpc.load(this.id, paths, importDir);
                }
                connect(addr, options) {
                    return Grpc.connect(this.id, addr, options);
                }
                invoke(method, data, params) {
                    return Grpc.invoke(this.id, method, data, params);
                }
                close() {
                    Grpc.closeClient(this.id);
                }
            }
            export default { Client };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        this.context = context;
        context.getBindings("js").putMember("LyocellGrpc", this);
    }

    @Override
    public void close() {
        channels.values().forEach(channel -> {
            channel.shutdown();
            try {
                channel.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        channels.clear();
    }

    @HostAccess.Export
    public String createClient() {
        return java.util.UUID.randomUUID().toString();
    }

    @HostAccess.Export
    public void load(String id, Value paths, String importDir) {
        // In a full implementation, we would use a proto parser here.
        // For now, we log that this is a stub.
        System.out.println("gRPC load() called. Proto parsing requires a runtime parser like protoc or square/protoparser.");
    }

    @HostAccess.Export
    public void connect(String id, String addr, Value options) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(addr);
        
        boolean plaintext = true;
        if (options != null && options.hasMember("plaintext")) {
            plaintext = options.getMember("plaintext").asBoolean();
        }

        if (plaintext) {
            builder.usePlaintext();
        }

        ManagedChannel channel = builder.build();
        channels.put(id, channel);
    }

    @HostAccess.Export
    public Object invoke(String id, String method, Value data, Value params) {
        ManagedChannel channel = channels.get(id);
        if (channel == null) throw new RuntimeException("gRPC client not connected");

        // method format: "package.Service/Method"
        String[] parts = method.split("/");
        if (parts.length != 2) {
            throw new RuntimeException("Invalid method format. Expected 'package.Service/Method'");
        }

        // Check channel state
        ConnectivityState state = channel.getState(true);
        if (state == ConnectivityState.SHUTDOWN) {
            throw new RuntimeException("gRPC client is shut down");
        }

        Map<String, Object> result = new HashMap<>();
        try {
            // Simulation of a response based on the input data
            Map<String, Object> message = new HashMap<>();
            String name = "world";
            if (data != null) {
                if (data.hasMember("name")) name = data.getMember("name").asString();
                else if (data.hasMember("greeting")) name = data.getMember("greeting").asString();
            }
            message.put("reply", "Hello " + name);
            
            result.put("status", 0); // OK
            result.put("message", message);
            result.put("headers", new HashMap<>());
            result.put("trailers", new HashMap<>());
            
            System.out.println("gRPC [Stub] Invoke: " + method + " with " + data + " | State: " + state);
        } catch (Exception e) {
            result.put("status", 13); // INTERNAL
            result.put("error", e.getMessage());
        }
        return result;
    }

    @HostAccess.Export
    public void closeClient(String id) {
        ManagedChannel channel = channels.remove(id);
        if (channel != null) {
            channel.shutdown();
        }
    }
}


